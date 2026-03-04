package com.example.ticketing.security;

import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;

public record CustomPrincipal(Long userId, Collection<? extends GrantedAuthority> authorities)
        implements UserDetails {

    @Override public String getUsername() { return userId.toString(); }
    @Override public String getPassword() { return ""; } // JWT stateless
    @Override public Collection<? extends GrantedAuthority> getAuthorities() { return authorities; }
}

