package com.yuankai.aispringboot.DTO.command;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class ConsultationSessionTitleUpdateDTO {
    // 会话标题
    @NotBlank(message = "会话标题不能为空")
    @Size(max = 50, message = "会话标题长度不能超过50个字符")
    private String sessionTitle;
}
