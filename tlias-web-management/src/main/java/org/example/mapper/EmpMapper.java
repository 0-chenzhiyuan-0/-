package org.example.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Select;
import org.example.pojo.Emp;

import java.util.List;

@Mapper
public interface EmpMapper {

    @Select("select count(*) from emp e left join dept d on e.dept_id = d.id")
    public long count();

    @Select("select e.*,d.name empName from emp e left join dept d on e.dept_id = d.id order by update_time desc limit #{start},#{pageSize}")
    public List<Emp> list(Integer start, Integer pageSize);

}
