package vn.suviet.util;

import jakarta.servlet.ServletContextEvent;
import jakarta.servlet.ServletContextListener;
import jakarta.servlet.annotation.WebListener;
import java.sql.Driver;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Enumeration;

@WebListener
public final class JdbcDriverCleanup implements ServletContextListener {
    @Override
    public void contextDestroyed(ServletContextEvent event) {
        ClassLoader loader = JdbcDriverCleanup.class.getClassLoader();
        Enumeration<Driver> drivers = DriverManager.getDrivers();
        while (drivers.hasMoreElements()) {
            Driver driver = drivers.nextElement();
            if (driver.getClass().getClassLoader() == loader) {
                try {
                    DriverManager.deregisterDriver(driver);
                }
                catch (SQLException e) {
                    event.getServletContext().log("Could not deregister application JDBC driver", e);
                }
            }
        }
    }
}
