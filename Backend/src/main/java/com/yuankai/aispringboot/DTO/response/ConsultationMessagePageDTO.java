package com.yuankai.aispringboot.DTO.response;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * 会话消息分页结果。
 *
 * <p>为什么不直接返回 {@code List}：「还有没有更早的消息」只有服务端知道 ——
 * 客户端拿到正好 50 条时，无法区分「刚好 50 条且已到最早」和「还有更多」。
 * 用 hasMore 显式告知后，前端才能正确决定是否保留「加载更早」入口。
 * （本地实测 50 条以上会话时，靠 {@code length === limit} 猜会在恰好整除时多出一次空请求。）</p>
 *
 * <p>本类只由 Controller 构造返回，不经 MyBatis 实例化，但仍显式声明无参构造器：
 * 统一遵守「DTO 必须有无参构造器」的约定，避免以后被 Mapper 复用时踩到
 * MyBatis 退回「按列顺序调构造器」的坑。</p>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ConsultationMessagePageDTO {

    /** 消息列表，按时间升序（最早的在最前，便于直接追加到对话区顶部） */
    private List<ConsultationMessageResponseDTO> records;

    /** 是否还有更早的消息；false 时前端不再展示「加载更早」 */
    private Boolean hasMore;
}
