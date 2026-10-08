package com.yuankai.aispringboot.service;

import cn.hutool.core.util.StrUtil;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.yuankai.aispringboot.DTO.query.OperationLogQueryDTO;
import com.yuankai.aispringboot.common.ResultCode;
import com.yuankai.aispringboot.entity.OperationLog;
import com.yuankai.aispringboot.exception.BusinessException;
import com.yuankai.aispringboot.mapper.OperationLogMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import java.time.LocalTime;

/**
 * 操作日志查询服务。
 *
 * 日志由 {@code OperationLogAspect} 切面自动写入，本服务只负责「读」这一侧 ——
 * 补上这块，@OperationLog 记下来的审计数据才不会只躺在库里没人看得到。
 * 请求参数在写入时已做脱敏（password / confirmPassword / newPassword / oldPassword），
 * 因此这里可以直接把整条记录返回给管理员。
 */
@Slf4j
@Service
public class OperationLogService {

    @Autowired
    private OperationLogMapper operationLogMapper;

    /** 管理员分页查询操作日志 */
    public Page<OperationLog> getLogPage(OperationLogQueryDTO queryDTO) {
        Page<OperationLog> page = new Page<>(queryDTO.getCurrentPage(), queryDTO.getSize());

        LambdaQueryWrapper<OperationLog> queryWrapper = new LambdaQueryWrapper<>();
        if (StrUtil.isNotBlank(queryDTO.getUsername())) {
            queryWrapper.like(OperationLog::getUsername, queryDTO.getUsername());
        }
        if (StrUtil.isNotBlank(queryDTO.getOperation())) {
            queryWrapper.like(OperationLog::getOperation, queryDTO.getOperation());
        }
        if (queryDTO.getStatus() != null) {
            queryWrapper.eq(OperationLog::getStatus, queryDTO.getStatus());
        }

        // 起止日期传反时直接拒绝，避免给出一条语义相反的空结果
        if (queryDTO.getStartDate() != null && queryDTO.getEndDate() != null
                && queryDTO.getStartDate().isAfter(queryDTO.getEndDate())) {
            throw new BusinessException(ResultCode.PARAM_ERROR.getCode(), "开始日期不能晚于结束日期");
        }
        if (queryDTO.getStartDate() != null) {
            queryWrapper.ge(OperationLog::getCreatedAt, queryDTO.getStartDate().atStartOfDay());
        }
        if (queryDTO.getEndDate() != null) {
            // 结束日是闭区间：补到当天最后一刻，否则当天产生的日志会被整体漏掉
            queryWrapper.le(OperationLog::getCreatedAt, queryDTO.getEndDate().atTime(LocalTime.MAX));
        }

        // 按 id 倒序而不是 created_at：同一秒内的多条日志时间相同，排序不稳定
        queryWrapper.orderByDesc(OperationLog::getId);

        return operationLogMapper.selectPage(page, queryWrapper);
    }
}
