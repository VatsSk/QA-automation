package com.testingautomation.testautomation.config.WebDriverConfig;

import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.remote.RemoteWebDriver;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;

@Component
public class WebDriverFactory {

    @Value("${spring.profiles.active:dev}")
    private String activeProfile;

    @Value("${selenium.remote-url:}")
    private String remoteUrl;

    @Value("${selenium.headless:false}")
    private boolean headless;

    public WebDriver createDriver() throws MalformedURLException {

        ChromeOptions options = new ChromeOptions();

        // Disable browser-level popups
        Map<String, Object> prefs = new HashMap<>();
        prefs.put("credentials_enable_service", false);
        prefs.put("profile.password_manager_enabled", false);
        prefs.put("profile.default_content_setting_values.notifications", 2);
        prefs.put("autofill.profile_enabled", false);
        prefs.put("autofill.credit_card_enabled", false);

        Path profile = null;
        try {
            profile = Files.createTempDirectory("selenium-profile");
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        options.addArguments("--user-data-dir=" + profile.toString());

        options.setExperimentalOption("prefs", prefs);

        // General browser arguments
        options.addArguments("--disable-gpu");
        options.addArguments("--disable-notifications");
        options.addArguments("--disable-popup-blocking");
        options.addArguments("--ignore-certificate-errors");
        options.addArguments("--disable-infobars");
        options.addArguments("--disable-save-password-bubble");

        // Accept insecure certificates
        options.setAcceptInsecureCerts(true);

        if (headless) {
            options.addArguments("--headless=new");
            options.addArguments("--window-size=1920,1080");

            // Helps normalize rendering on different DPI displays
            options.addArguments("--force-device-scale-factor=1");
            options.addArguments("--high-dpi-support=1");
        }

        if ("prod".equals(activeProfile)) {
            options.addArguments("--no-sandbox");
            options.addArguments("--disable-dev-shm-usage");

            WebDriver driver = new RemoteWebDriver(
                    new URL(remoteUrl),
                    options
            );

            if (!headless) {
                driver.manage().window().maximize();
            }

            return driver;
        }

        WebDriver driver = new ChromeDriver(options);

        // Maximize only when not headless
        if (!headless) {
            driver.manage().window().maximize();
        }

        return driver;
    }

//    public WebDriver createDriver() throws MalformedURLException {
//
//        ChromeOptions options = new ChromeOptions();
//
//        if (headless) {
//            options.addArguments("--headless=new");
//        }
//
//        options.addArguments("--disable-gpu");
//        options.addArguments("--window-size=1534,664");
//
//        if ("prod".equals(activeProfile)) {
//            options.addArguments("--no-sandbox");
//            options.addArguments("--disable-dev-shm-usage");
//
//            return new RemoteWebDriver(
//                    new URL(remoteUrl),
//                    options
//            );
//        }
//
//        return new ChromeDriver(options);
//    }
}