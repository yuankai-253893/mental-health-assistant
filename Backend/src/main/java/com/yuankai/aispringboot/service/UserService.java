package com.yuankai.aispringboot.service;

import com.auth0.jwt.interfaces.DecodedJWT;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.command.UserLoginCommandDTO;
import com.yuankai.aispringboot.DTO.command.UserRegisterCommandDTO;
import com.yuankai.aispringboot.DTO.query.UserQueryDTO;
import com.yuankai.aispringboot.common.ResultCode;
import com.yuankai.aispringboot.consts.RedisKeyConsts;
import com.yuankai.aispringboot.DTO.response.UserLoginResponseDTO;
import com.yuankai.aispringboot.entity.User;
import com.yuankai.aispringboot.enumclass.UserStatus;
import com.yuankai.aispringboot.enumclass.UserType;
import com.yuankai.aispringboot.exception.BusinessException;
import com.yuankai.aispringboot.mapper.UserMapper;
import com.yuankai.aispringboot.service.convert.UserConvert;
import com.yuankai.aispringboot.util.JwtTokenUtil;
import com.yuankai.aispringboot.util.RedisCounterUtil;
import cn.hutool.core.util.StrUtil;
import jakarta.annotation.Resource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import java.time.LocalDateTime;
import java.util.List;
import java.util.concurrent.TimeUnit;

import static com.yuankai.aispringboot.util.JwtTokenUtil.generateToken;

@Service
public class UserService {
    private static final Logger log = LoggerFactory.getLogger(UserService.class);

    // 登录防暴力破解：同一账号失败达到阈值后锁定
    private static final int MAX_LOGIN_FAIL_TIMES = 5;               // 最大失败次数阈值
    private static final long LOCK_MINUTES = 15;                     // 锁定时长：15分钟

    // 注册 IP 限流：同一 IP 一天最多注册的账号数（防脚本批量注册）
    private static final int MAX_REGISTER_TIMES_PER_IP = 3;
    private static final long REGISTER_LIMIT_DAYS = 1;               // 统计窗口：1 天

    @Resource
    private UserMapper userMapper;

    @Resource
    private RedisTokenBlacklist redisTokenBlacklist;

    @Resource
    private RedisCounterUtil redisCounterUtil;

    @Resource
    private ActiveUserRecordService activeUserRecordService;

    private final BCryptPasswordEncoder PasswordEncoder = new BCryptPasswordEncoder();

    public UserLoginResponseDTO login(UserLoginCommandDTO commandDTO) {
        // 1. 防暴力破解：先检查该账号失败次数是否已达阈值（Redis 计数）
        //    fail-open：Redis 不可用时查询抛异常 → 视为 0 次失败，跳过限流检查，保证登录不因 Redis 故障而中断
        String loginFailKey = RedisKeyConsts.LOGIN_FAIL_PREFIX + commandDTO.getUsername();     // 键 login:fail:用户名
        String failCountStr = null;
        try {
            failCountStr = redisCounterUtil.get(loginFailKey);                 // 获取失败次数
        } catch (Exception e) {
            log.warn("登录限流查询失败（Redis 不可用？），fail-open 跳过限流检查", e);
        }
        if (failCountStr != null && parseFailCount(failCountStr) >= MAX_LOGIN_FAIL_TIMES) {
            throw new BusinessException("登录失败次数过多，请" + LOCK_MINUTES + "分钟后再试");
        }

        // 构建查询条件
        LambdaQueryWrapper<User> userquery = new LambdaQueryWrapper<>();
        userquery.eq(User::getUsername, commandDTO.getUsername())
                .or()
                .eq(User::getEmail, commandDTO.getUsername());
        // 调用MP API查询
        User user = userMapper.selectOne(userquery);

        log.debug("查询用户: {}", user);
        // 判断用户是否存在
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), ResultCode.USER_NOT_EXIST.getMsg());
        }

        // 验证密码
        String inputPassword = commandDTO.getPassword().trim();
        // 防御：库中密码若非BCrypt格式（脏数据/手动改库），matches会抛异常导致500，统一按密码错误处理
        if (user.getPassword() == null || !user.getPassword().startsWith("$2")) {
            recordLoginFail(loginFailKey);
            throw new BusinessException("密码错误");
        }
        if (!PasswordEncoder.matches(inputPassword, user.getPassword())) {
            recordLoginFail(loginFailKey);
            throw new BusinessException("密码错误");
        }

        // 检查用户状态
        if (!user.isActive()) {
            throw new BusinessException("用户已禁用");
        }

        // 登录成功：清除该账号的失败计数（fail-open：Redis 不可用时忽略，不影响登录成功返回）
        try {
            redisCounterUtil.delete(loginFailKey);
        } catch (Exception e) {
            log.warn("清除登录失败计数失败（Redis 不可用？），fail-open 继续", e);
        }

        // 活跃埋点：登录成功视为一次今日活跃（Redis HyperLogLog 去重计数）
        activeUserRecordService.record(user.getId());

        // 生成token
        String token = generateToken(user.getId(), user.getUsername(), user.getUserType());
        log.info("用户 {} 登录成功", user.getUsername());
        UserLoginResponseDTO.UserDetailResponseDTO userInfo = UserConvert.entityToDetailResponse(user);
        return UserConvert.entityToDetailResponse(token, userInfo);
    }

    // 记录一次登录失败：Redis INCR 原子自增 + 刷新锁定时长（15分钟）
    // fail-open：Redis 不可用时只记录 WARN，不阻断"密码错误"这一正常业务返回
    private void recordLoginFail(String loginFailKey) {
        try {
            Long count = redisCounterUtil.incrementWithExpire(loginFailKey, LOCK_MINUTES, TimeUnit.MINUTES);
            log.warn("账号 {} 登录失败第 {} 次", loginFailKey.replace(RedisKeyConsts.LOGIN_FAIL_PREFIX, ""), count);
        } catch (Exception e) {
            log.warn("记录登录失败次数失败（Redis 不可用？），fail-open 继续返回密码错误", e);
        }
    }

    // 安全解析失败次数：Redis 中值被写坏（脏数据/手动改库成非数字）时按 0 处理，
    // 避免 Integer.parseInt 抛异常导致登录 500（与 fail-open 策略一致，登录不被非关键数据阻断）
    private int parseFailCount(String failCountStr) {
        try {
            return Integer.parseInt(failCountStr);
        } catch (NumberFormatException e) {
            log.warn("登录失败次数值异常（{}），按 0 处理", failCountStr);
            return 0;
        }
    }

    // ---------------- 注册 IP 限流 ----------------

    // 检查当前 IP 当日注册数是否已达上限：达上限直接抛业务异常，未达则放行
    // 注意：这里是"先查后增"，极端并发下（同一 IP 同一天几乎同时发起 3 次注册）可能短暂越过上限，
    // 对个人项目可接受；若需严格原子可改为 INCR 后判断、超限 DECR 回退（会多一次写）
    private void checkRegisterIpLimit() {
        String countStr = null;
        try {
            countStr = redisCounterUtil.get(RedisKeyConsts.REGISTER_LIMIT_PREFIX + getClientIp());
        } catch (Exception e) {
            log.warn("注册限流查询失败（Redis 不可用？），fail-open 放行", e);
            return;
        }
        if (countStr != null && parseFailCount(countStr) >= MAX_REGISTER_TIMES_PER_IP) {
            throw new BusinessException("同一 IP 一天最多注册 " + MAX_REGISTER_TIMES_PER_IP + " 个账号，请明天再试");
        }
    }

    // 注册成功后计数 +1，并刷新 TTL（1 天）
    private void recordRegisterIp() {
        try {
            redisCounterUtil.incrementWithExpire(
                    RedisKeyConsts.REGISTER_LIMIT_PREFIX + getClientIp(),
                    REGISTER_LIMIT_DAYS, TimeUnit.DAYS);
        } catch (Exception e) {
            log.warn("记录注册次数失败（Redis 不可用？），fail-open 继续", e);
        }
    }

    // 获取客户端 IP：直接取连接地址。
    private String getClientIp() {
        ServletRequestAttributes attrs = (ServletRequestAttributes) RequestContextHolder.getRequestAttributes();
        if (attrs != null) {
            return attrs.getRequest().getRemoteAddr();
        }
        return "unknown";
    }

    public UserLoginResponseDTO.UserDetailResponseDTO register(UserRegisterCommandDTO commandDTO) {
        log.info("用户注册: {}", commandDTO.getUsername());

        // IP 维度注册限流：同一 IP 一天最多注册 3 个账号（防批量注册/灌垃圾账号）
        // fail-open：Redis 不可用时查询抛异常 → 视为未注册放行，注册不因 Redis 故障而中断
        checkRegisterIpLimit();

        // 验证密码是否一致
        if (!commandDTO.getPassword().equals(commandDTO.getConfirmPassword())) {
            throw new BusinessException("两次输入密码不一致");
        }

        // 检查用户名是否存在
        LambdaQueryWrapper<User> usernamequery = new LambdaQueryWrapper<>();
        usernamequery.eq(User::getUsername, commandDTO.getUsername());
        if (userMapper.selectCount(usernamequery) > 0) {
            throw new BusinessException(ResultCode.ACCOUNT_SAME.getCode(), ResultCode.ACCOUNT_SAME.getMsg());
        }

        // 检查邮箱是否存在
        LambdaQueryWrapper<User> emailquery = new LambdaQueryWrapper<>();
        emailquery.eq(User::getEmail, commandDTO.getEmail());
        if (userMapper.selectCount(emailquery) > 0) {
            throw new BusinessException("邮箱已存在");
        }

        // 安全校验：注册接口只允许创建普通用户，禁止通过注册创建管理员
        Integer regType = commandDTO.getUserType() == null ? UserType.USER.getCode() : commandDTO.getUserType();
        if (!UserType.USER.getCode().equals(regType)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "注册仅支持普通用户类型");
        }

        // 创建用户
        String password = commandDTO.getPassword().trim();
        String encodedPassword = PasswordEncoder.encode(password);
        User user = UserConvert.registerCommandToEntity(commandDTO, encodedPassword);

        // 输入数据库
        userMapper.insert(user);

        // 注册成功后才计数（失败的注册请求不消耗 IP 配额）
        recordRegisterIp();

        return UserConvert.entityToDetailResponse(user);
    }

    public UserLoginResponseDTO.UserDetailResponseDTO getUserById(Long id) {
        log.info("用户查询: {}", id);
        User user = userMapper.selectById(id);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), ResultCode.USER_NOT_EXIST.getMsg());
        }
        return UserConvert.entityToDetailResponse(user);
    }

    public void logout(Long userId) {
        log.info("用户 {} 登出", userId);
        // 获取当前 Token
        String token = JwtTokenUtil.getCurrentToken();
        if (token != null) {
            // 解析 Token 过期时间，计算剩余有效期（防止redis内存无限增大）
            DecodedJWT jwt = JwtTokenUtil.verifyToken(token);
            long expireMillis = jwt.getExpiresAt().getTime() - System.currentTimeMillis();
            // 将 Token 加入 Redis 黑名单
            redisTokenBlacklist.addToBlacklist(token, expireMillis);
        }
    }

    // ==================== 管理端用户管理 ====================

    /**
     * 用户分页查询（管理员）。
     * 返回的是 UserDetailResponseDTO 而不是 User 实体 —— 实体带 password 字段，
     * 一旦直接序列化就会把密码哈希泄露给前端。
     */
    public Page<UserLoginResponseDTO.UserDetailResponseDTO> getUserPage(UserQueryDTO queryDTO) {
        Page<User> page = new Page<>(queryDTO.getCurrentPage(), queryDTO.getSize());

        LambdaQueryWrapper<User> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(queryDTO.getUsername())) {
            queryWrapper.like(User::getUsername, queryDTO.getUsername());
        }
        if (StrUtil.isNotBlank(queryDTO.getNickname())) {
            queryWrapper.like(User::getNickname, queryDTO.getNickname());
        }
        if (queryDTO.getStatus() != null) {
            queryWrapper.eq(User::getStatus, queryDTO.getStatus());
        }
        if (queryDTO.getUserType() != null) {
            queryWrapper.eq(User::getUserType, queryDTO.getUserType());
        }
        // 新注册用户排在前面，便于管理员第一时间看到
        queryWrapper.orderByDesc(User::getId);

        Page<User> userPage = userMapper.selectPage(page, queryWrapper);

        List<UserLoginResponseDTO.UserDetailResponseDTO> records = userPage.getRecords().stream()
                .map(UserConvert::entityToDetailResponse)
                .toList();

        Page<UserLoginResponseDTO.UserDetailResponseDTO> responsePage =
                new Page<>(userPage.getCurrent(), userPage.getSize(), userPage.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    /**
     * 修改用户状态（禁用 / 启用）。
     *
     * 禁用后无需额外处理 token：JwtAuthenticationFilter 每次请求都会查一次用户状态，
     * 命中 isActive()==false 直接返回 token 已被禁止访问，旧 token 立即失效。
     *
     * @param operatorId 当前操作者 id，用于拦截「管理员禁用自己」这种自锁操作
     */
    public void updateUserStatus(Long userId, Integer status, Long operatorId) {
        if (!UserStatus.NORMAL.getCode().equals(status) && !UserStatus.DISABLED.getCode().equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "用户状态只能是 0(禁用) 或 1(正常)");
        }

        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), ResultCode.USER_NOT_EXIST.getMsg());
        }

        // 不允许禁用自己：否则会把自己锁在后台之外，只能改数据库才能恢复
        if (userId.equals(operatorId) && UserStatus.DISABLED.getCode().equals(status)) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "不能禁用当前登录的管理员账号");
        }

        if (status.equals(user.getStatus())) {
            return;
        }

        User update = new User();
        update.setId(userId);
        update.setStatus(status);
        update.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(update);

        log.info("管理员{}将用户{}（{}）状态修改为{}", operatorId, userId, user.getUsername(),
                UserStatus.fromCode(status).getDescription());
    }

    /**
     * 重置用户密码（管理员）。
     *
     * 已知限制：JWT 是无状态的，改密码不会让已签发的 token 失效
     * （token 里不携带密码信息）。若需要「改密即踢下线」，得再引入
     * 按用户的失效时间戳，当前未实现。
     */
    public void resetPassword(Long userId, String newPassword) {
        User user = userMapper.selectById(userId);
        if (user == null) {
            throw new BusinessException(ResultCode.USER_NOT_EXIST.getCode(), ResultCode.USER_NOT_EXIST.getMsg());
        }

        User update = new User();
        update.setId(userId);
        update.setPassword(PasswordEncoder.encode(newPassword.trim()));
        update.setUpdatedAt(LocalDateTime.now());
        userMapper.updateById(update);

        // 清掉该账号残留的登录失败计数，避免用新密码登录时被旧的锁定计数拦住
        try {
            redisCounterUtil.delete(RedisKeyConsts.LOGIN_FAIL_PREFIX + user.getUsername());
        } catch (Exception e) {
            log.warn("清除用户{}登录失败计数失败（Redis 不可用？）", user.getUsername(), e);
        }

        log.info("管理员重置了用户{}（{}）的密码", userId, user.getUsername());
    }
}
