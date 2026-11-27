package com.example.bowling.config;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.userdetails.*;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

@Configuration
@EnableMethodSecurity
public class SecurityConfig {
 @Bean PasswordEncoder passwordEncoder(){return new BCryptPasswordEncoder();}
 @Bean SecurityFilterChain chain(HttpSecurity http)throws Exception{
  return http.authorizeHttpRequests(a->a.requestMatchers("/css/**","/error").permitAll().anyRequest().hasRole("ADMIN"))
   .formLogin(f->f.defaultSuccessUrl("/lanes",true).permitAll()).logout(l->l.logoutSuccessUrl("/login")).build();
 }
 @Bean @Profile("demo") UserDetailsService demoUsers(PasswordEncoder encoder,@Value("${bowling.demo.admin-password:local-demo-only}") String password){
  return new InMemoryUserDetailsManager(User.withUsername("admin").password(encoder.encode(password)).roles("ADMIN").build());
 }
 @Bean @Profile("!demo") UserDetailsService dbUsers(JdbcTemplate jdbc){
  return login->jdbc.query("SELECT login_id,password_hash,role,status FROM MEMBER WHERE login_id=?",
   (rs,i)->User.withUsername(rs.getString(1)).password(rs.getString(2)).roles(rs.getString(3)).disabled(!"ACTIVE".equals(rs.getString(4))).build(),login)
   .stream().findFirst().orElseThrow(()->new UsernameNotFoundException("계정이 없습니다."));
 }
}
