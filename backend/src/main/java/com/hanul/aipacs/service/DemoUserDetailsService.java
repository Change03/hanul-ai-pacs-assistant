package com.hanul.aipacs.service;

import com.hanul.aipacs.repository.UserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;

@Service
public class DemoUserDetailsService implements UserDetailsService {
    private final UserRepository users;

    public DemoUserDetailsService(UserRepository users) {
        this.users = users;
    }

    @Override
    public UserDetails loadUserByUsername(String username) throws UsernameNotFoundException {
        return users.findByUsername(username)
            .map(user -> User.withUsername(user.getUsername())
                .password(user.getPasswordHash())
                .roles(user.getRole().name())
                .build())
            .orElseThrow(() -> new UsernameNotFoundException("Unknown demo user"));
    }
}
