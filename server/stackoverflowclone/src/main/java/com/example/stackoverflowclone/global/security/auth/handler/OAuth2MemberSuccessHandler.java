package com.example.stackoverflowclone.global.security.auth.handler;


import com.example.stackoverflowclone.domain.member.dto.MemberLoginResponseDto;
import com.example.stackoverflowclone.domain.member.entity.Member;
import com.example.stackoverflowclone.domain.member.service.MemberService;
import com.example.stackoverflowclone.global.response.DataResponseDto;
import com.example.stackoverflowclone.global.security.auth.jwt.JwtTokenizer;
import com.example.stackoverflowclone.global.security.auth.utils.CustomAuthorityUtils;
import com.google.gson.Gson;
import lombok.AllArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.SimpleUrlAuthenticationSuccessHandler;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.util.UriComponentsBuilder;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.net.URI;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


@Slf4j
@AllArgsConstructor
public class OAuth2MemberSuccessHandler extends SimpleUrlAuthenticationSuccessHandler {
    private final JwtTokenizer jwtTokenizer;
    private final CustomAuthorityUtils authorityUtils;
    private final MemberService memberService;

    @Override
    public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response, Authentication authentication) throws IOException, ServletException {
        // log.info("# Redirect to Frontend");
        OAuth2User oAuth2User = (OAuth2User) authentication.getPrincipal();
        String name = (String) oAuth2User.getAttributes().get("name");
        String email = (String) oAuth2User.getAttributes().get("email");
        String picture = (String) oAuth2User.getAttributes().get("picture");

        // log.info("# getPrincipal : " + oAuth2User);
        // log.info("# name : "+ name);
        // log.info("# email : "+ email);
        // log.info("# picture : "+ picture);

        // email??? ????????? Member ?????? ???????????? DB??? ??????
        Member member = buildOAuth2Member(name, email, picture);
        Member saveMember = saveMember(member);

        // ?????? email ????????? ?????? List ?????????
        List<String> authorities = authorityUtils.createRoles(email);

        // ?????????????????? ???????????? ???????????? ?????????
        redirect(request,response,saveMember,authorities);
    }
    private Member buildOAuth2Member(String name, String email, String picture){
        return Member.builder()
                .username(name)
                .email(email)
                .password("")
                .location("")
                .aboutMe("")
                .title("")
                .fullname("")
                .image(picture)
                .websiteLink("")
                .twitterLink("")
                .githubLink("")
                .fullname("")
                .build();
    }

    public Member saveMember(Member member){
        return memberService.createMemberOAuth2(member);
    }

    private void redirect(HttpServletRequest request, HttpServletResponse response, Member member, List<String> authorities) throws IOException {
        // ?????? ????????? ????????? AccessToken, Refresh Token??? ??????
        String accessToken = delegateAccessToken(member, authorities);
        String refreshToken = delegateRefreshToken(member);

        // Token??? ????????? URI??? ???????????? String?????? ??????
        String uri = createURI(request, accessToken, refreshToken).toString();

        // ????????? ???????????????
        String headerValue = "Bearer "+ accessToken;
        response.setHeader("Authorization",headerValue); // Header??? ??????
        response.setHeader("Refresh",refreshToken); // Header??? ??????
        // response.setHeader("Access-Control-Allow-Credentials:", "true");
        // response.setHeader("Access-Control-Allow-Origin", "*");
        // response.setHeader("Access-Control-Expose-Headers", "Authorization");

        // ?????? URI??? ??????????????? ??????
        getRedirectStrategy().sendRedirect(request,response,uri);
    }

    private String delegateAccessToken(Member member, List<String> authorities){

        Map<String,Object> claims = new HashMap<>();
        claims.put("roles", member.getRoles());
        claims.put("memberId", member.getMemberId());

        String subject = member.getEmail();
        Date expiration = jwtTokenizer.getTokenExpiration(jwtTokenizer.getAccessTokenExpirationMinutes());
        String base64EncodedSecretKey = jwtTokenizer.encodeBase64SecretKey(jwtTokenizer.getSecretKey());
        return jwtTokenizer.generateAccesToken(claims, subject, expiration, base64EncodedSecretKey);
    }

    private String delegateRefreshToken(Member member){
        String subject = member.getEmail();
        Date expiration = jwtTokenizer.getTokenExpiration(jwtTokenizer.getRefreshTokenExpirationMinutes());
        String base64EncodedSecretKey = jwtTokenizer.encodeBase64SecretKey(jwtTokenizer.getSecretKey());
        return jwtTokenizer.generateRefreshToken(subject, expiration, base64EncodedSecretKey);
    }

    private URI createURI(HttpServletRequest request, String accessToken, String refreshToken){
        // ?????????????????? JWT??? URI??? ????????? ??????
        MultiValueMap<String, String> queryParams = new LinkedMultiValueMap<>();
        queryParams.add("access_token", accessToken);
        queryParams.add("refresh_token", refreshToken);

        String serverName = request.getServerName();
        // log.info("# serverName = {}",serverName);

        return UriComponentsBuilder
                .newInstance()
                .scheme("http")
                .host(serverName)
                //.host("localhost")
                .port(80) // ?????? ????????? 80?????? ????????? ?????????
                .path("/token")
                .queryParams(queryParams)
                .build()
                .toUri();
    }
}
