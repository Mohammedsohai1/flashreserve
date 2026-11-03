package com.example.flashreserve.service;

import com.example.flashreserve.entity.AppUser;
import com.example.flashreserve.repository.AppUserRepository;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class CustomUserDetailsService implements UserDetailsService {

    private final AppUserRepository userRepository;

    public CustomUserDetailsService(AppUserRepository userRepository) {
        this.userRepository = userRepository;
    }

    @Override
    @Transactional(readOnly = true)
    public UserDetails loadUserByUsername(String email) {
        AppUser appUser = userRepository.findByEmailIgnoreCase(email)
                .orElseThrow(() ->
                        new UsernameNotFoundException("Invalid email or password"));

        return User.withUsername(appUser.getEmail())
                .password(appUser.getPasswordHash())
                .authorities("ROLE_" + appUser.getRole().name())
                .disabled(!appUser.isEnabled())
                .build();
    }
}