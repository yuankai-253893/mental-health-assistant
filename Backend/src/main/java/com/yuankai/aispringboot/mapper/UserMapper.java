package com.yuankai.aispringboot.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.yuankai.aispringboot.entity.User;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface UserMapper extends BaseMapper<User> {

    @Select("""
        SELECT DATE(created_at) AS date,
               COUNT(*) AS cnt
        FROM user
        WHERE created_at >= DATE_SUB(CURDATE(), INTERVAL 6 DAY)
        GROUP BY DATE(created_at)
        """)
    List<Map<String, Object>> selectLast7DaysNewUsers();
}
