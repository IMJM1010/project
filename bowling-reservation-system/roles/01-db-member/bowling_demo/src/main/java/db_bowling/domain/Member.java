package db_bowling.domain;

import java.util.Date;

public class Member {
    private Long memberId;
    private String name;
    private String phone;
    private String email;
    private String grade;
    private Date createdAt;

    public Long getMemberId() { return memberId; }
    public void setMemberId(Long memberId) { this.memberId = memberId; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }
    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }
    public String getGrade() { return grade; }
    public void setGrade(String grade) { this.grade = grade; }
    public Date getCreatedAt() { return createdAt; }
    public void setCreatedAt(Date createdAt) { this.createdAt = createdAt; }
}