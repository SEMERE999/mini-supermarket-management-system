package com.minisupermarket.service;

import com.minisupermarket.dao.UserRepository;
import com.minisupermarket.model.AbstractUser;
import java.sql.SQLException;

public class AuthenticationService {
    private final UserRepository userRepository;

    public AuthenticationService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public AbstractUser login(String username, String password) throws SQLException {
        return userRepository.findByUsername(username)
            .filter(AbstractUser::isActive)
            .filter(user -> user.matchesPassword(password))
            .orElse(null);
    }
}
