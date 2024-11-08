package com.teamx.exsite.service.user;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import com.teamx.exsite.model.mapper.user.UserMapper;
import com.teamx.exsite.model.user.dto.UserDTO;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class UserService {
	
	private final UserMapper userMapper;
	private final PasswordEncoder passwordEncoder;
	private final AuthService authService;
	private final APIService apiService;
	
	public int userRegister(UserDTO registerInfo) {
		String encodePass = passwordEncoder.encode(registerInfo.getUserPw());
		registerInfo.setUserPw(encodePass);
		return userMapper.registerUser(registerInfo);
	}
	
	public int idCheck(String id) {
		return userMapper.idCheck(id);
	}
	
	public UserDTO basicLogin(UserDTO loginInfo) {
		UserDTO idSelectResult = userMapper.basicLogin(loginInfo);
		if(idSelectResult == null) {
			return null;
		}
		if(passwordEncoder.matches(loginInfo.getUserPw(), idSelectResult.getUserPw())) {
			return idSelectResult;
		}
		return null;
	}

	public int nameCheck(String name) {
		int result = userMapper.searchUserName(name);
		return result;
	}
	
	public int accountCheck(String signupMethod, String email) {
		return userMapper.accountCheck(signupMethod, email);
	}

	public String idSearch(String authMethod) {
		return userMapper.idSearch(authMethod);
	}
	
	public String idSearch(String authMethod, String loginMethod) {
		return userMapper.idSearch(authMethod, loginMethod);
	}

	public int passwordChange(String userId, String name, String authMethod, String changePassword) {
		String encodedPassword = passwordEncoder.encode(changePassword);
		return userMapper.passwordChange(userId, name, authMethod, encodedPassword);
	}

	public Map<String, String> naverUserRegistration(UserDTO user, HttpSession session) {
	    Map<String, String> result = new HashMap<>();
	    if (accountCheck("NAVER", user.getEmail()) == 0 && identifierCheck(user.getSocialUserIdentifier()) == 0) {
	        int signupResult = userMapper.registerWithNaver(user);
	        if (signupResult > 0) {
	            result.put("status", "success");
	            session.setAttribute("loginUser", user);
	        } else {
	        	result.put("status", "false");
	        }
	    }
	    return result;
	}

	private int identifierCheck(String socialUserIdentifier) {
		return userMapper.identifierCheck(socialUserIdentifier);
	}

	public Map<String, String> naverUserLogin(UserDTO user, HttpSession session) {
	    Map<String, String> result = new HashMap<>();
	    // 네이버로 가입한 이메일인지 확인, 맞으면 로그인 유저 객체 session에 담아 로그인 성공 응답
	    if (accountCheck("NAVER", user.getEmail()) == 1 && identifierCheck(user.getSocialUserIdentifier()) == 1) {
	        result.put("status", "success");
	        session.setAttribute("loginUser", user);
	    // 네이버로 가입한 이메일이 아닐 때, 이미 사용중인 이메일인지 확인, 존재하는 이메일이면 exist 응답
	    } else if(authService.mailCheck(user.getEmail()) == 1 || authService.phoneCheck(user.getPhone()) == 1) {
	    	result.put("status", "exist");
	    // 둘 다 아니면 false, -> 사용자 응답 확인 후 naverUserRegistration 실행여부 결정
	    } else {
	    	result.put("status", "false");
	    }
	    
	    
	    return result;
	}
	
	public Map<String, String> googleUserRegistration(UserDTO user, HttpSession session) {
	    Map<String, String> result = new HashMap<>();
	    if (accountCheck("GOOGLE", user.getEmail()) == 0 && identifierCheck(user.getSocialUserIdentifier()) == 0) {
	        int signupResult = userMapper.registerWithGoogle(user);
	        if (signupResult > 0) {
	            result.put("status", "success");
	            session.setAttribute("loginUser", user);
	        } else {
	        	result.put("status", "false");
	        }
	    }
	    return result;
	}
	
	public Map<String, String> googleUserLogin(String code, HttpSession session) {
		Map<String, String> result = new HashMap<>();
		
		JSONObject userInfo = apiService.googleUserInfoGetProcess(code);
		int accountCheck = accountCheck("GOOGLE", userInfo.getString("email"));
		int identifierCheck = identifierCheck(userInfo.getString("sub"));
		int mailCheck = authService.mailCheck(userInfo.getString("email"));
		if(accountCheck == 1 && identifierCheck == 1) {
			UserDTO loginUser = new UserDTO();
			loginUser.setUserId(idSearch(userInfo.getString("email"), "GOOGLE"));
			loginUser.setName(userInfo.getString("name"));
			loginUser.setEmail(userInfo.getString("email"));
			loginUser.setSocialUserIdentifier(userInfo.getString("sub"));
			session.setAttribute("loginUser", loginUser);
			result.put("status", "success");
		} else if(mailCheck == 1) {
			result.put("status", "exist");
		} else {
			JSONObject registrationInfo = new JSONObject();
			registrationInfo.put("name", userInfo.getString("name"));
			registrationInfo.put("socialUserIdentifier", userInfo.getString("sub"));
			registrationInfo.put("email", userInfo.getString("email"));
			result.put("registrationInfo", registrationInfo.toString());
			result.put("status", "false");
		}
		return result;
	}
	

}
