package com.teamx.exsite.controller.user;

import java.util.HashMap;
import java.util.Map;

import org.json.JSONObject;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;

import com.teamx.exsite.model.user.dto.UserDTO;
import com.teamx.exsite.service.user.AuthService;
import com.teamx.exsite.service.user.UserService;

import jakarta.servlet.http.HttpSession;
import lombok.RequiredArgsConstructor;

@Controller
@RequiredArgsConstructor
public class UserController {

	private final UserService userService;
	private final AuthService authService;
	private Map<String, String> userInfoCache = new HashMap<>();

	@GetMapping("/signup")
	public String enrollForm() {
		return "/user/enrollForm";
	}
	
	/**
	 * @param session
	 * @param registerInfo 회원가입 폼에서 입력한 유저 정보
	 * @return 메인 페이지로 리다이렉트
	 */
	@RequestMapping("/user/register")
	public String userRegister(HttpSession session, UserDTO registerInfo) {
		// 회원가입에서 상세 받은 상세주소(addressDetail)를 Address에 합침, DB 설계에 상세주소 컬럼이 따로 없음
		registerInfo.setAddress(registerInfo.getAddress() + "=" + registerInfo.getAddressDetail());
		int result = userService.userRegister(registerInfo);
		if (result == 1) {
			UserDTO loginUser = registerInfo;
			session.setAttribute("loginUser", loginUser);
		}
		return "redirect:/";
	}

	@GetMapping("/login")
	public String loginForm(Model model, @RequestParam(value = "userId", required = false) String userId) {
		if (userId != null)
			model.addAttribute("userId", userId);
		return "/user/loginForm";
	}

	@GetMapping("/logout")
	public String basicLogout(HttpSession session) {
		session.invalidate();
		return "redirect:/";
	}

	@ResponseBody
	@PostMapping(value = "/login/normal", produces = "application/json; charset=utf-8")
	public Map<String, String> basicLogin(HttpSession session, UserDTO loginInfo) {
		Map<String, String> result = new HashMap<>();
		if ((loginInfo = userService.basicLogin(loginInfo)) != null) {
			// 로그인 성공 시 "success"를 반환
			session.setAttribute("loginUser", loginInfo);
			result.put("response", "success");
			return result;
		}
		// 실패 시 "false"를 반환
		result.put("response", "false");
		return result;
	}
	
	@GetMapping("/login/naver/callback")
	public String naverLoginCallback() {
		return "/user/naverCallbackPage";
	}
	
	/**
	 * @param naverSignupUser 네이버에서 제공하는 유저 개인정보를 담음
	 * @param mobile 네이버 제공 휴대폰 번호가 "-" 포함되어 오므로, 따로 받아서 "-" 제거 후 UserDTO 병합
	 * @param session
	 * @return 성공 여부를 Map<String, String> 으로 반환 "success", "false"
	 */
	@ResponseBody
	@PostMapping(value="/user/register/naver", produces="application/json; charset=utf-8")
	public Map<String, String> signupWithNaver(UserDTO naverSignupUser, String mobile, HttpSession session) {
		// 네이버에서 주는 휴대폰 정보에 "-"가 포함되어 제거함
		naverSignupUser.setPhone(mobile.replace("-", ""));
		naverSignupUser.setMethod("NAVER");
		return userService.naverUserRegistration(naverSignupUser, session);
	}
	
	/**
	 * @param naverLoginUser 네이버에서 제공하는 유저 개인정보를 담음
	 * @param mobile 네이버 제공 휴대폰 번호가 "-" 포함되어 오므로, 따로 받아서 "-" 제거 후 UserDTO 병합
	 * @param session
	 * @return 성공 여부를 Map<String, String> 으로 반환 "success", "exist", "false"
	 */
	@ResponseBody
	@PostMapping(value="/login/naver", produces="application/json; charset=utf-8")
	public Map<String, String> naverLogin(UserDTO naverLoginUser, String mobile, HttpSession session) {
		naverLoginUser.setPhone(mobile.replace("-", ""));
		naverLoginUser.setMethod("NAVER");
		return userService.naverUserLogin(naverLoginUser, session);
	}
	
	/**
	 * @param registerKey 로그인 시도 후 미가입한 회원인 경우, 로그인 메서드에서 받아온 유저정보를 해당 값을 키값으로 Map에 임시 저장
	 * @param session
	 * @return Google 회원가입 의사 확인 후 맞으면 회원가입 처리 후 메인페이지로, 아니면 임시정보 삭제 후 로그인 페이지로 리다이렉트
	 */
	@PostMapping(value="/user/register/google", produces="application/json; charset=utf-8")
	public String signupWithGoogle(String registerKey, HttpSession session) {
		// key
		String IntentionToJoin = registerKey.substring(6);
		if(IntentionToJoin.equals("not")) {
			//"not"
			userInfoCache.remove(registerKey.substring(0, 6));
			return "/user/loginForm";
		}
		JSONObject userInfo = new JSONObject(userInfoCache.get(registerKey));
		UserDTO registerationInfo = new UserDTO();
		registerationInfo.setName(userInfo.getString("name"));
		registerationInfo.setSocialUserIdentifier(userInfo.getString("socialUserIdentifier"));
		registerationInfo.setEmail(userInfo.getString("email"));
		registerationInfo.setMethod("GOOGLE");
		userService.googleUserRegistration(registerationInfo, session);
		userInfoCache.remove(registerKey);
		return "redirect:/";
	}
	
	/**
	 * @param error 유저가 google로그인 동의 후 실패되면 오는 값
	 * @param code 유저가 google 로그인 동의 시 google 서버에서 전달하는 유저 정보 accessToken code
	 * @param scope 유저 정보 조회 범위
	 * @param session
	 * @param model
	 * @return 성공이면 메인페이지, 존재하는 경우 로그인 페이지, 실패인 경우 가입 여부 확인하는 googleCallbackPage 리다이렉트
	 */
	@RequestMapping("/login/google")
	public String googleLogin(@RequestParam(value="error", defaultValue="") String error
									, @RequestParam(value="code", defaultValue="") String code
									, @RequestParam(value="scope", defaultValue="") String scope
									, HttpSession session
									, Model model) {
		
		
		if(code != null && !code.isEmpty()) {
			// UserService.googleUserLogin => 구글 Login 메서드, ApiService쪽에 googleLogin 처리 관련 메서드 모아놓음
			Map<String, String> result = userService.googleUserLogin(code, session);
			if(result.get("status").equals("success")) {
				return "redirect:/";
			} else if(result.get("status").equals("exist")) {
				model.addAttribute("alertMsg", "사이트에서 가입한 이메일입니다."); 
				return "/user/loginForm";
			} else if(result.get("status").equals("false")) {
				String registerKey = authService.generateAuthCode();
				model.addAttribute("registerKey", registerKey);
				userInfoCache.put(registerKey, result.get("registrationInfo"));
				
				model.addAttribute("alertMsg", "가입하지 않은 회원입니다. 가입하시겠습니까?");
				return "/user/googleCallbackPage";
			}
		
		}
		return "redirect:/";
	}

	@ResponseBody
	@PostMapping(value = "/id/check", produces = "application/json; charset=utf-8")
	public int idCheck(String userId) {
		return userService.idCheck(userId);
	}

	/**
	 * @param email 이메일 정보
	 * @param phone 휴대폰 정보
	 * @return "exist": 이미 사용중인 휴대폰, 이메일인 경우 / "ok": 해당 인증도구로 인증코드 발송한 경우
	 * @throws Exception
	 */
	@ResponseBody
	@PostMapping(value = "/signup/authcode", produces = "application/json; charset=utf-8")
	public Map<String, String> signupAuth(@RequestParam(value="email", required=false) String email
									  , @RequestParam(value="phone", required=false) String phone) throws Exception {
		// 휴대폰 인증, 메일 인증 둘 다 사용 가능한 메서드, 두 값 중 없는 건 null 값 허용
		// 있는것만 받아서 둘 중 하나를 key값으로, value로 6자리 인증코드를 LocalDateTime 객체를 같이 묶어서 
		//authService의 verificationCodes Map에 임시저장
		if(email == null) {
			return authService.processAuth(phone, "phone");
		} else {
			return authService.processAuth(email, "email");
		}
	}

	/**
	 * @param email 이메일 정보
	 * @param phone 휴대폰 정보
	 * @param code 사용자가 보낸 인증코드
	 * @return "timeout": 인증 유효시간 초과, "success": 인증 성공, "false": 인증 실패, 실패인 경우 verificationCode 맵에서 삭제하지 않음
	 */
	@ResponseBody
	@PostMapping(value = "/signup/authcode/verify", produces = "application/json; charset=utf-8;")
	public Map<String, String> returnSignupAuthResult(@RequestParam(value="email", required=false)String email
													, @RequestParam(value="phone", required=false)String phone
													, String code) {
		//사용자가 인증 요청을 할 때 이메일이나 휴대폰 번호 중 하나를 받아서 verifyCode 메서드로 성공여부 반환
		String key = email == null ? phone : email;
		return authService.verifyCode(key, code);
	}
	


	// --------------------------계정정보 메일 인증 기능 -------------------------------
	/**
	 * @param name 아이디/비밀번호 찾기 시 사용자가 입력한 실명
	 * @param email 이메일 정보
	 * @param phone 휴대폰 정보
	 * @return "success": 성공인 경우 인증번호 발송 / "notfound": 실패인 경우 반환
	 * @throws Exception
	 */
	@ResponseBody
	@PostMapping(value = "/account/recover/auth")
	public Map<String, String> AuthNumberTransfer(String name
													 , @RequestParam(value="email", required=false) String email
													 , @RequestParam(value="phone", required=false) String phone) throws Exception {
		
		Map<String, String> response = new HashMap<>();
		int nameCheckResult = userService.nameCheck(name.trim());
		if(phone == null) {
			int mailCheckResult = authService.mailCheck(email.trim());
			if (mailCheckResult == 1 && nameCheckResult == 1) {
				authService.javaMailSender(name, email);
				response.put("status", "success");
			} else {
				response.put("status", "notfound");
			}
		} else if(email == null) {
			int phoneCheckResult = authService.phoneCheck(phone.trim());
			if (phoneCheckResult == 1 && nameCheckResult == 1) {
				authService.sendSMS(name, phone);
				response.put("status", "success");
			} else {
				response.put("status", "notfound");
			}
		} else {
			response.put("status", "notfound");
		}
		
		return response;
	}

	/**
	 * @param name 사용자가 입력한 실명
	 * @param email 이메일 정보
	 * @param phone 휴대폰 정보
	 * @param code 사용자가 입력한 인증코드
	 * @return
	 */
	@ResponseBody
	@PostMapping(value = "/account/recover/auth/verify", produces = "application/json; charset=utf-8;")
	public Map<String, String> returnAuthResult(String name
											  , @RequestParam(value="email", required=false) String email
											  , @RequestParam(value="phone", required=false) String phone
											  , String code) {
		
		String authMethod = email == null ? phone : email;
		Map<String, String> result = authService.verifyCode(name + authMethod, code);
		String userId = userService.idSearch(authMethod);
		if(userId == null) {
			result.put("userId", "notFound");
		} else {
			result.put("userId", userId);
		}
		
		return result;
	}
	// ------------------------------------------------------------------------
	// ------------------------ 아이디 찾기 ----------------------------------------

	@GetMapping("/id/recover")
	public String findIdForm() {
		return "/user/findIdForm";
	}

	@GetMapping("/id/recover/result")
	public String findIdResult() {
		return "/user/findIdResultForm";
	}

	// -------------------------------------------------------------------------
	// ------------------------ 비밀번호 재설정 ----------------------------------------
	/**
	 * @param userId 아이디 찾기로 id를 찾은 후 비밀번호 재설정 페이지로 넘어오는 경우, 해당 페이지에서 받은 userId값
	 * @param model
	 * @return
	 */
	@GetMapping("/password/recover/first")
	public String findPasswordFirstForm(@RequestParam(value = "userId", required = false) String userId, Model model) {
		if (userId != null) {
			model.addAttribute("userId", userId);
		}
		return "/user/findPasswordFirstForm";
	}

	/**
	 * @param userId 비밀번호 재설정 1차 페이지에서 idCheck 메서드로 확인된 userId
	 * @param model
	 * @return 비밀번호 재설정 2차 페이지
	 */
	@PostMapping("/password/recover/second")
	public String findPasswordSecondForm(String userId, Model model) {
		if (userId != null) {
			model.addAttribute("userId", userId);
		}
		return "/user/findPasswordSecondForm";
	}

	/**
	 * @param userId 비밀번호 재설정 2차 페이지에서 넘긴 userId
	 * @param name 비밀번호 재설정 2차 페이지에서 사용자가 입력한 실명
	 * @param email 사용자가 입력한 이메일 정보
	 * @param phone 사용자가 입력한 휴대폰 정보
	 * @param model
	 * @return
	 */
	@PostMapping("/password/change/form")
	public String passwordchangeForm(String userId
								   , String name
								   , @RequestParam(value="email", required=false) String email
								   , @RequestParam(value="phone", required=false) String phone
								   , Model model) {
		
		String authMethod = email == null ? phone : email;
		model.addAttribute("userId", userId);
		model.addAttribute("name", name);
		model.addAttribute("authMethod", authMethod);
		// 비밀번호 재설정 페이지로 같이 넘길 6자리 인증번호
		String code = authService.generateAuthCode();
		// email 또는 phone + name + userId를 key값으로, code를 value로 AuthService.verificationCodes에 저장
		authService.generateAuthInfo(authMethod + name + userId, code);
		// code를 비밀번호 재설정 페이지로 같이 넘김
		model.addAttribute("code", code);
		System.out.println(code);
		return "/user/changePasswordForm";
	}
	
	/**
	 * @param userId 비밀번호 재설정 2차 페이지에서 javascript sessionStorage에 담아 넘긴 userId 
	 * @param name 비밀번호 재설정 2차 페이지에서 javascript sessionStorage에 담아 넘긴 name
	 * @param authMethod 비밀번호 재설정 2차 페이지에서 javascript sessionStorage에 담아 넘긴 authMethod
	 * @param code 컨트롤러에서 비밀번호 재설정 페이지로 넘긴 code값 
	 * @param changePassword 사용자가 입력한 재설정 비밀번호
	 * @param model
	 * @return "timeout": 코드 유효시간 초과 / "success": 초기화 성공 / "false": 인증 실패
	 */
	@PostMapping("/password/change")
	public String passwordchange(String userId
							   , String name
							   , String authMethod
							   , String code
							   , String changePassword
							   , Model model) {
		String result = authService.verifyCode(authMethod + name + userId, code).get("status");
		System.out.println(code);
		if (result.equals("timeout")) {
			model.addAttribute("alertMsg", "인증 유효시간이 초과되었습니다.");
			return "redirect:/";
		} else if (result.equals("success")) {
			int passwordchangeResult = userService.passwordChange(userId, name, authMethod, changePassword);
			if (passwordchangeResult == 1) {
				model.addAttribute("alertMsg", "비밀번호가 초기화되었습니다.");
			} else {
				model.addAttribute("alertMsg", "오류가 발생했습니다. 관리자에게 문의 바랍니다.");
			}
		} else if (result.equals("false")) {
			model.addAttribute("alertMsg", "오류가 발생했습니다. 관리자에게 문의 바랍니다.");

		}

		return "redirect:/";
	}

	// -------------------------------------------------------------------------
}
