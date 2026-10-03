package com.kun.onlinejudge.security;

import com.kun.onlinejudge.constant.HeaderConstant;
import java.io.IOException;
import javax.servlet.FilterChain;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

@Component
public class IdentityHeaderFilter extends OncePerRequestFilter {

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain filterChain) throws ServletException, IOException {
        setLong(request, HeaderConstant.HEADER_USER_ID, HeaderConstant.ATTR_USER_ID);
        setString(request, HeaderConstant.HEADER_USER_ROLE, HeaderConstant.ATTR_USER_ROLE);
        setString(request, HeaderConstant.HEADER_USER_NAME, HeaderConstant.ATTR_USER_NAME);
        setString(request, HeaderConstant.HEADER_DEVICE_TYPE, HeaderConstant.ATTR_DEVICE_TYPE);
        filterChain.doFilter(request, response);
    }

    private void setLong(HttpServletRequest request, String header, String attr) {
        String v = request.getHeader(header);
        if (v != null) {
            try {
                request.setAttribute(attr, Long.valueOf(v));
            } catch (NumberFormatException ignored) {
                request.setAttribute(attr, null);
            }
        }
    }

    private void setString(HttpServletRequest request, String header, String attr) {
        String v = request.getHeader(header);
        if (v != null) {
            request.setAttribute(attr, v);
        }
    }
}
