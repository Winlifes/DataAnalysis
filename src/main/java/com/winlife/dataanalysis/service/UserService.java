package com.winlife.dataanalysis.service;

import com.winlife.dataanalysis.model.User;
import com.winlife.dataanalysis.repository.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Service
public class UserService {

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private PasswordEncoder passwordEncoder;  // 这里使用 PasswordEncoder，而不是 BCryptPasswordEncoder

    public User register(User user) {
        user.setPassword(passwordEncoder.encode(user.getPassword()));
        return userRepository.save(user);
    }

    public Optional<User> findByUsername(String username) {
        return userRepository.findByUsername(username);
    }

    public boolean changePassword(String username, String oldPassword, String newPassword) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if (passwordEncoder.matches(oldPassword, user.getPassword())) {
                user.setPassword(passwordEncoder.encode(newPassword));
                userRepository.save(user);
                return true;
            }
        }
        return false;
    }

    public boolean bindContact(String username, String type, String value) {
        Optional<User> optionalUser = userRepository.findByUsername(username);
        if (optionalUser.isPresent()) {
            User user = optionalUser.get();
            if ("phone".equals(type)) {
                user.setPhone(value);
            } else if ("email".equals(type)) {
                user.setEmail(value);
            } else if ("nickname".equals(type)) {
                user.setNickname(value);
            }
            else {
                return false;
            }
            userRepository.save(user);
            return true;
        }
        return false;
    }

    public void deleteUser(Long id) {
        userRepository.deleteById(id);
    }

    public void updateUser(Long id, User data) {
        User user = userRepository.findById(id).orElseThrow();
        if (data.getPassword() != null && !data.getPassword().isEmpty()) {
            user.setPassword(passwordEncoder.encode(data.getPassword()));
        }
        user.setNickname(data.getNickname());
        user.setAuthCheck(data.isAuthCheck());
        user.setAuthEdit(data.isAuthEdit());
        user.setAuthExport(data.isAuthExport());
        user.setAuthAdmin(data.isAuthAdmin());
        userRepository.save(user);
    }
}
