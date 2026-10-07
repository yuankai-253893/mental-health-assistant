package com.yuankai.aispringboot.service;

import cn.hutool.core.date.DateUtil;
import cn.hutool.core.util.StrUtil;
import cn.hutool.json.JSONUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.command.ConsultationSessionCreateDTO;
import com.yuankai.aispringboot.DTO.query.ConsultationSessionQueryDTO;
import com.yuankai.aispringboot.DTO.response.ConsultationMessageResponseDTO;
import com.yuankai.aispringboot.DTO.response.ConsultationSessionResponseDTO;
import com.yuankai.aispringboot.DTO.response.EmotionAnalysisResponseDTO;
import com.yuankai.aispringboot.common.ResultCode;
import com.yuankai.aispringboot.entity.ConsultationMessage;
import com.yuankai.aispringboot.entity.ConsultationSession;
import com.yuankai.aispringboot.entity.User;
import com.yuankai.aispringboot.enumclass.UserType;
import com.yuankai.aispringboot.exception.BusinessException;
import com.yuankai.aispringboot.mapper.ConsultationMessageMapper;
import com.yuankai.aispringboot.mapper.ConsultationSessionMapper;
import com.yuankai.aispringboot.mapper.UserMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;

@Slf4j
@Service
public class ConsultationSessionService {
    @Autowired
    private UserMapper userMapper;

    @Autowired
    private ConsultationSessionMapper consultationSessionMapper;

    @Autowired
    private ConsultationMessageMapper consultationMessageMapper;

    @Autowired
    private ConsultationMessageService consultationMessageService;

    public ConsultationSession createSession (Long userId, ConsultationSessionCreateDTO createDTO){
        // 验证用户是否存在
        User user = userMapper.selectById(userId);
        if (user != null) {
            // 创建会话记录
            ConsultationSession session = ConsultationSession.builder()
                    .userId(userId)
                    .sessionTitle(createDTO.getSessionTitle())
                    .startedAt(LocalDateTime.now())
                    .build();
            // 如果未提供标题
            if (StrUtil.isBlank(createDTO.getSessionTitle())) {
                // 设置默认标题
                session.setSessionTitle("AI助手-" + DateUtil.format(LocalDateTime.now(), "MM-dd HH:mm"));
            }

            // 插入记录
            consultationSessionMapper.insert(session);
            return session;

        }
        return null;
    }

    // 根据会话ID查询会话，用于校验会话归属
    public ConsultationSession getConsultationSessionBySessionId(Long sessionId) {
        return consultationSessionMapper.selectById(sessionId);
    }

    // 修改会话标题
    @Transactional
    public void updateSessionTitle(Long sessionId, String sessionTitle) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND.getCode(), ResultCode.SESSION_NOT_FOUND.getMsg());
        }
        session.setSessionTitle(sessionTitle);
        consultationSessionMapper.updateById(session);
    }

    /**
     * 分页查询会话。
     *
     * @param userId  当前登录用户 id
     * @param roleType 当前登录用户角色（{@link UserType}），决定查询范围：
     *                 管理员查全部用户，普通用户只查自己
     */
    public Page<ConsultationSessionResponseDTO> getSessionsByPage(Long userId, Integer roleType, ConsultationSessionQueryDTO queryDTO) {
        // 构建分页对象
        Page<ConsultationSession> page = new Page<>(queryDTO.getCurrentPage(), queryDTO.getSize());

        // 构建查询条件
        LambdaQueryWrapper<ConsultationSession> queryWrapper = new LambdaQueryWrapper<>();

        // 查询范围：管理员可查看全部用户的会话；普通用户只能查看自己的会话。
        // 条件为 true 时才拼接，管理员这个 eq 不会生效（不是查 userId 为空的记录）。
        boolean isAdmin = UserType.ADMIN.getCode().equals(roleType);
        queryWrapper.eq(!isAdmin, ConsultationSession::getUserId, userId);

        // 管理员可通过 userId 进一步缩小到某个用户的会话；
        // 普通用户即使传了 userId 也无效——上面的 eq 已把自己锁定，这里不再叠加条件，
        // 两个条件同时存在时是 AND 关系，不会造成越权。
        if (isAdmin && queryDTO.getUserId() != null) {
            queryWrapper.eq(ConsultationSession::getUserId, queryDTO.getUserId());
        }

        // 如果提供了情绪标签，按最后情绪分析结果模糊匹配
        if (StrUtil.isNotBlank(queryDTO.getEmotionTag())) {
            queryWrapper.like(ConsultationSession::getLastEmotionAnalysis, queryDTO.getEmotionTag());
        }

        // 按开始时间倒序排列
        queryWrapper.orderByDesc(ConsultationSession::getStartedAt);

        // 执行分页查询
        Page<ConsultationSession> sessionPage = consultationSessionMapper.selectPage(page, queryWrapper);
        // 转换为响应DTO（顺带批量补全会话所属用户名，管理员查看全部时便于区分归属）
        return convertToResponsePage(sessionPage);
    }

    // Page 转换：逐条转 DTO 后统一回填用户名
    private Page<ConsultationSessionResponseDTO> convertToResponsePage(Page<ConsultationSession> sessionPage) {
        if (sessionPage.getRecords().isEmpty()) {
            return new Page<>(sessionPage.getCurrent(), sessionPage.getSize(), sessionPage.getTotal());
        }

        List<ConsultationSessionResponseDTO> records = sessionPage.getRecords().stream()
                .map(this::convertToResponseDTO)
                .toList();

        // 批量查询涉及到的用户，避免逐条查库
        Set<Long> userIds = records.stream()
                .map(ConsultationSessionResponseDTO::getUserId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
        if (!userIds.isEmpty()) {
            Map<Long, String> usernameMap = userMapper.selectByIds(userIds).stream()
                    .collect(Collectors.toMap(User::getId, User::getUsername, (a, b) -> a));
            records.forEach(dto -> dto.setUsername(usernameMap.get(dto.getUserId())));
        }

        Page<ConsultationSessionResponseDTO> responsePage =
                new Page<>(sessionPage.getCurrent(), sessionPage.getSize(), sessionPage.getTotal());
        responsePage.setRecords(records);
        return responsePage;
    }

    private ConsultationSessionResponseDTO convertToResponseDTO(ConsultationSession session) {
        ConsultationSessionResponseDTO responseDTO = new ConsultationSessionResponseDTO();
        responseDTO.setId(session.getId());
        responseDTO.setUserId(session.getUserId());
        responseDTO.setSessionTitle(session.getSessionTitle());
        responseDTO.setStartedAt(session.getStartedAt());
        responseDTO.setLastEmotionAnalysis(session.getLastEmotionAnalysis());
        responseDTO.setLastEmotionUpdatedAt(session.getLastEmotionUpdatedAt());

        // 查询该会话的消息数量
        LambdaQueryWrapper<ConsultationMessage> countWrapper = new LambdaQueryWrapper<>();
        countWrapper.eq(ConsultationMessage::getSessionId, session.getId());
        Long messageCount = consultationMessageMapper.selectCount(countWrapper);
        responseDTO.setMessageCount(messageCount.intValue());

        // 补充最后一条消息，用于列表展示会话预览与最近时间
        ConsultationMessageResponseDTO lastMessage = consultationMessageService.getLastMessageBySessionId(session.getId());
        if (lastMessage != null) {
            responseDTO.setLastMessageContent(lastMessage.getContent());
            responseDTO.setLastMessageTime(lastMessage.getCreatedAt());
        }

        return responseDTO;
    }

    // 删除会话，并级联删除该会话下的所有消息
    @Transactional(rollbackFor = Exception.class)   // 失败后回滚
    public void deleteSession(Long sessionId, Long userId) {
        // 先级联删除会话下的消息，避免残留孤儿数据
        LambdaQueryWrapper<ConsultationMessage> messageWrapper = new LambdaQueryWrapper<>();
        messageWrapper.eq(ConsultationMessage::getSessionId, sessionId);
        consultationMessageMapper.delete(messageWrapper);

        // 再删除会话本身，通过删除行数判断会话是否存在
        int deletedRows = consultationSessionMapper.deleteById(sessionId);
        if (deletedRows == 0) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND.getCode(), ResultCode.SESSION_NOT_FOUND.getMsg());
        }

        log.info("用户{}删除会话，id: {}", userId, sessionId);

    }

    // 读取会话的情绪分析结果
    public EmotionAnalysisResponseDTO getEmotionAnalysisBySessionId(Long sessionId) {
        ConsultationSession session = consultationSessionMapper.selectById(sessionId);
        if (session == null) {
            throw new BusinessException(ResultCode.SESSION_NOT_FOUND.getCode(), ResultCode.SESSION_NOT_FOUND.getMsg());
        }

        EmotionAnalysisResponseDTO responseDTO = new EmotionAnalysisResponseDTO();
        responseDTO.setSessionId(session.getId());
        responseDTO.setLastEmotionUpdatedAt(session.getLastEmotionUpdatedAt());

        // 情绪分析结果以JSON字符串存库，解析后返回
        if (StrUtil.isNotBlank(session.getLastEmotionAnalysis())) {
            responseDTO.setLastEmotionAnalysis(JSONUtil.parse(session.getLastEmotionAnalysis()));
        }
        return responseDTO;
    }
}
