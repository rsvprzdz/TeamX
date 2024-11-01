package com.teamx.exsite.controller.mypage;

import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;

@Controller
public class MypageController {

	@GetMapping("/mypage/main")
	public String mypageMain() {
		return "/mypage/mypageMain";
	}
}
