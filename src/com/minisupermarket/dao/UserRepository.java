package com.minisupermarket.dao;

import com.minisupermarket.model.AbstractUser;
import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

public interface UserRepository {
    Optional<AbstractUser> findByUsername(String username) throws SQLException;
List<AbstractUser> findAll() throws SQLException;
void save(AbstractUser user) throws SQLException;
void deactivate(int userId) throws SQLException;
void updateManagerProfile(int userId, String fullName, String newPasswordHash) throws SQLException;
void promoteToManager(int userId) throws SQLException;
    
}
