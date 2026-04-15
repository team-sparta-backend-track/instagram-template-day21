package com.example.instagramclone.core.controller;

import jakarta.servlet.RequestDispatcher;
import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.web.servlet.error.ErrorController;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;

import java.util.Map;

@Controller
@Slf4j
public class RouteController implements ErrorController {

    @GetMapping("/")
    public String index() {
        return "index";
    }

    @RequestMapping("/error")
    public Object handleError(HttpServletRequest request) {
        Object status = request.getAttribute(RequestDispatcher.ERROR_STATUS_CODE);
        String uri = (String) request.getAttribute(RequestDispatcher.ERROR_REQUEST_URI);

        log.debug("Error occurred for URI: {}, Status: {}", uri, status);

        // API 경로에서 발생한 에러는 빈 응답 (또는 적절한 JSON 형식)으로 넘김
        if (uri != null && uri.startsWith("/api/")) {
            if (status != null) {
                return ResponseEntity.status(Integer.parseInt(status.toString()))
                        .body(Map.of("error", "API Error", "status", status));
            }
            return ResponseEntity.badRequest().build();
        }

        // 그 외 일반 경로의 404(Not Found) 등은 React Router 처리를 위해 index.html 반환
        return "index";
    }
}
