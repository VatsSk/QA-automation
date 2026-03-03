package com.testingautomation.testautomation.scan;
import com.testingautomation.testautomation.model.FieldDescriptor;
import com.testingautomation.testautomation.model.UiElement;
import io.github.bonigarcia.wdm.WebDriverManager;
import org.openqa.selenium.*;
import org.openqa.selenium.chrome.ChromeDriver;
import org.openqa.selenium.chrome.ChromeOptions;
import org.openqa.selenium.support.ui.ExpectedConditions;
import org.openqa.selenium.support.ui.WebDriverWait;
import org.springframework.stereotype.Service;

import java.io.FileDescriptor;
import java.time.Duration;
import java.util.*;

@Service
public class UiScannerService {

    public List<FieldDescriptor> scanPage(String url) {
        System.out.println("Scan Page");

        WebDriverManager.chromedriver().setup();

        ChromeOptions options = new ChromeOptions();
        options.addArguments("--headless=new");
        options.addArguments("--disable-gpu", "--no-sandbox", "--disable-dev-shm-usage");
        options.addArguments("--window-size=1366,768");

        WebDriver driver = new ChromeDriver(options);

        try {
            driver.get(url);
            Thread.sleep(5000); // wait for page load

            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script = """
                return Array.from(document.querySelectorAll(
                             'input, textarea, select, button, a, [role="button"], [onclick], [tabindex]'
                           ))
                           .filter(function(el) {
                             const rect = el.getBoundingClientRect();
                             const style = window.getComputedStyle(el);
                    
                             return rect.width > 0 &&
                                    rect.height > 0 &&
                                    style.visibility !== 'hidden' &&
                                    style.display !== 'none' &&
                                    el.id; // <-- ignore if id is null or empty
                           })
                           .map(function(el) {
                    
                             let text = null;
                    
                             if (el.tagName.toLowerCase() === 'select') {
                               if (el.selectedIndex >= 0 && el.options.length > 0) {
                                 text = el.options[el.selectedIndex].text.trim();
                               } else if (el.options.length > 0) {
                                 text = el.options[0].text.trim();
                               }
                             } else {
                               text = el.innerText ? el.innerText.trim() : null;
                             }
                    
                             const id = el.id;
                             const name = el.name || null;
                    
                             const css = '#' + id;
                    
                             const xpath = '//*[@id="' + id + '"]';
                    
                             return {
                               tag: el.tagName.toLowerCase(),
                               type: el.type || null,
                               id: id,
                               name: name,
                               text: text,
                               css: css,
                               xpath: xpath
                             };
                           });
            """;

            List<Map<String, Object>> raw =
                    (List<Map<String, Object>>) js.executeScript(script);

            List<FieldDescriptor> elements = new ArrayList<>();

            for (Map<String, Object> map : raw) {
                FieldDescriptor e = new FieldDescriptor();
                e.tag = (String) map.get("tag");
                e.type = (String) map.get("type");
                e.id = (String) map.get("id");
                e.name = (String) map.get("name");
                e.text = (String) map.get("text");
                e.css = (String) map.get("css");
                e.xpath = (String) map.get("xpath");
                elements.add(e);
            }

            return elements;

        } catch (Exception e) {
            throw new RuntimeException(e);
        } finally {
            driver.quit();
        }
    }

    public List<FieldDescriptor> scanPage(String url,WebDriver driver) {
        System.out.println("Scan Page overloaded");
        try {
            driver.get(url);
            System.out.println("after url calling");
//            Thread.sleep(5000); // wait for page load
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));


            // 1️⃣ Wait for HTML to finish loading
            wait.until(webDriver ->
                    ((JavascriptExecutor) webDriver)
                            .executeScript("return document.readyState")
                            .equals("complete")
            );

            // 2️⃣ Wait until UI framework mounts something clickable
            wait.until(ExpectedConditions.presenceOfElementLocated(
                    By.cssSelector("button, a, input, select")
            ));
            System.out.println("wait is over");

            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script = """
                return Array.from(document.querySelectorAll(
                             'input, textarea, select, button, a, [role="button"], [onclick], [tabindex]'
                           ))
                           .filter(function(el) {
                             const rect = el.getBoundingClientRect();
                             const style = window.getComputedStyle(el);
                    
                             return rect.width > 0 &&
                                    rect.height > 0 &&
                                    style.visibility !== 'hidden' &&
                                    style.display !== 'none' ;
                           })
                           .map(function(el) {
                    
                             let text = null;
                    
                             if (el.tagName.toLowerCase() === 'select') {
                               if (el.selectedIndex >= 0 && el.options.length > 0) {
                                 text = el.options[el.selectedIndex].text.trim();
                               } else if (el.options.length > 0) {
                                 text = el.options[0].text.trim();
                               }
                             } else {
                               text = el.innerText ? el.innerText.trim() : null;
                             }
                    
                             const id = el.id;
                             const name = el.name || null;
                    
                             const css = '#' + id;
                    
                             const xpath = '//*[@id="' + id + '"]';
                    
                             return {
                               tag: el.tagName.toLowerCase(),
                               type: el.type || null,
                               id: id,
                               name: name,
                               text: text,
                               css: css,
                               xpath: xpath
                             };
                           });
            """;

            List<Map<String, Object>> raw =
                    (List<Map<String, Object>>) js.executeScript(script);

            System.out.println("raw size : "+ raw.size());

            List<FieldDescriptor> elements = new ArrayList<>();

            int count=0;

            for (Map<String, Object> map : raw) {
                count++;
                FieldDescriptor e = new FieldDescriptor();
                e.tag = (String) map.get("tag");
                e.type = (String) map.get("type");
                e.id = (String) map.get("id");
                e.name = (String) map.get("name");
                e.text = (String) map.get("text");
                e.css = (String) map.get("css");
                e.xpath = (String) map.get("xpath");
                elements.add(e);
            }


            return elements;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }
}

