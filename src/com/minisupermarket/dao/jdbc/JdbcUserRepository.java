package com.minisupermarket.dao.jdbc;

import com.minisupermarket.config.ConnectionFactory;
import com.minisupermarket.dao.UserRepository;
import com.minisupermarket.model.AbstractUser;
import com.minisupermarket.model.Cashier;
import com.minisupermarket.model.Manager;
import com.minisupermarket.model.Role;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

public class JdbcUserRepository implements UserRepository {
    @Override
    public Optional<AbstractUser> findByUsername(String username) throws SQLException {
        String sql = """
            SELECT UserId, FullName, Username, PasswordHash, Role, IsActive
            FROM dbo.AppUser
            WHERE Username = ?
            """;

        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql)) {
            statement.setString(1, username);
            try (ResultSet resultSet = statement.executeQuery()) {
                if (resultSet.next()) {
                    return Optional.of(mapUser(resultSet));
                }
            }
        }
        return Optional.empty();
    }

    @Override
    public List<AbstractUser> findAll() throws SQLException {
        String sql = """
            SELECT UserId, FullName, Username, PasswordHash, Role, IsActive
            FROM dbo.AppUser
            ORDER BY Role, FullName
            """;
        List<AbstractUser> users = new ArrayList<>();
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet resultSet = statement.executeQuery()) {
            while (resultSet.next()) {
                users.add(mapUser(resultSet));
            }
        }
        return users;
    }

    @Override
    public void save(AbstractUser user) throws SQLException {
        String sql = "{call dbo.sp_RegisterUser(?, ?, ?, ?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setString(1, user.getFullName());
            statement.setString(2, user.getUsername());
            statement.setString(3, user.getPasswordHash());
            statement.setString(4, user.getRole().name());
            statement.execute();
        }
    }
    @Override
    public void deactivate(int userId) throws SQLException {
        String sql = "{call dbo.sp_DeactivateUser(?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, userId);
            statement.execute();
        }
    }

    @Override
    public void updateManagerProfile(int userId, String fullName, String newPasswordHash) throws SQLException {
        String sql = "{call dbo.sp_UpdateManagerProfile(?, ?, ?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, userId);
            statement.setString(2, fullName);
            statement.setString(3, newPasswordHash);
            statement.execute();
        }
    }

    @Override
    public void promoteToManager(int userId) throws SQLException {
        String sql = "{call dbo.sp_PromoteToManager(?)}";
        try (Connection connection = ConnectionFactory.createConnection();
             PreparedStatement statement = connection.prepareCall(sql)) {
            statement.setInt(1, userId);
            statement.execute();
        }
    }
    

    private AbstractUser mapUser(ResultSet resultSet) throws SQLException {
        int userId = resultSet.getInt("UserId");
        String fullName = resultSet.getString("FullName");
        String username = resultSet.getString("Username");
        String passwordHash = resultSet.getString("PasswordHash");
        Role role = Role.fromDatabaseValue(resultSet.getString("Role"));
        boolean active = resultSet.getBoolean("IsActive");

        if (role == Role.MANAGER) {
            return new Manager(userId, fullName, username, passwordHash, active);
        }
        return new Cashier(userId, fullName, username, passwordHash, active);
    }
}
