package org.example.pojo;

import lombok.Data;

import java.time.LocalDate;
import java.time.LocalDateTime;

@Data
public class Emp {
    private Integer id;
    private String username;
    private String password;
    private String name;
    private Integer gender;
    private String phone;
    private String image;
    private String job;
    private Integer salary;
    private Integer deptId;
    private LocalDate entryDate;
    private LocalDateTime createTime;
    private LocalDateTime updateTime;

    private String deptName;
}
