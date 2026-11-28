package com.example.bowling.tools;
import java.io.Console;
import java.util.Arrays;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
/** Local CLI only; not a Spring endpoint. Never commit the output or password. */
public final class OracleAdminHashTool {
 public static void main(String[] args){
  Console console=System.console();
  if(console==null)throw new IllegalStateException("Run java directly in a terminal with a console");
  char[] password=console.readPassword("Local test administrator password: ");
  if(password==null)throw new IllegalArgumentException("No password");
  try {
   if(password.length<12||new String(password).getBytes(java.nio.charset.StandardCharsets.UTF_8).length>72)throw new IllegalArgumentException("Use at least 12 characters and at most 72 UTF-8 bytes");
   System.out.println(new BCryptPasswordEncoder().encode(java.nio.CharBuffer.wrap(password)));
  } finally {Arrays.fill(password,'\0');}
 }
}
