package com.minisupermarket.service;

import com.minisupermarket.dao.UserRepository;
import com.minisupermarket.model.AbstractUser;
import com.minisupermarket.model.Cashier;
import com.minisupermarket.model.Manager;
import com.minisupermarket.model.Role;
import com.minisupermarket.util.PasswordUtil;
import java.sql.SQLException;
import java.util.List;

public class UserManagementService {
    private final UserRepository userRepository;

    public UserManagementService(UserRepository userRepository) {
        this.userRepository = userRepository;
    }

    public void registerUser(String fullName, String username, String password, Role role) throws SQLException {
        AbstractUser user = role == Role.MANAGER
            ? new Manager(0, fullName, username, PasswordUtil.hash(password), true)
            : new Cashier(0, fullName, username, PasswordUtil.hash(password), true);
        userRepository.save(user);
    }

    public List<AbstractUser> listUsers() throws SQLException {
        return userRepository.findAll();
    }
    public void deactivateCashier(int userId) throws SQLException {
        userRepository.deactivate(userId);
    }

    public void updateManagerProfile(int userId, String fullName, String newPassword) throws SQLException {
        String newPasswordHash = PasswordUtil.hash(newPassword);
        userRepository.updateManagerProfile(userId, fullName, newPasswordHash);
    }

    public void promoteToManager(int userId) throws SQLException {
        userRepository.promoteToManager(userId);
    }
    
}
