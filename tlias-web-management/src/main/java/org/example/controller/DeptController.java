package org.example.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.example.pojo.Dept;
import org.example.pojo.Result;
import org.example.service.DeptService;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
public class DeptController {
    @Autowired
    private DeptService deptService;
    @GetMapping("/depts")
    public Result list(){
        System.out.println("查询全部部门信息");
        List<Dept> deptlist = deptService.findAll();
        return Result.success(deptlist);

    }
    @DeleteMapping("/depts")
    public Result delete(Integer id){
        System.out.println("根据ID删除部门信息"+id);
        deptService.deleteById(id);
        return Result.success();
    }
    @PostMapping("/depts")
    public Result add(@RequestBody Dept dept){
        System.out.println("添加部门信息"+dept);
        deptService.add(dept);
        return Result.success();
    }
    @GetMapping("/depts/{id}")
    public Result getById(@PathVariable Integer id ){
        System.out.println("修改部门信息"+id);
        Dept dept = deptService.getById(id);
        return Result.success(dept);
    }
    @PutMapping("/depts")
    public Result update(@RequestBody Dept dept){
        System.out.println("修改部门信息"+dept);
        deptService.update(dept);
        return Result.success();
    }

}
