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
                             'input, textarea, select, button, a, [role="button"], [onclick], [tabindex],'
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

            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(25));

            /* 1️⃣ Wait until browser finishes loading the document */
            wait.until(d ->
                    ((JavascriptExecutor) d)
                            .executeScript("return document.readyState")
                            .equals("complete")
            );

            /* 2️⃣ Wait until the body is present */
            wait.until(ExpectedConditions.presenceOfElementLocated(By.tagName("body")));

            /* 3️⃣ Wait until interactive elements appear */
            wait.until(d ->
                    (Long) ((JavascriptExecutor) d)
                            .executeScript(
                                    "return document.querySelectorAll('button,a,input,select,textarea').length"
                            ) > 0
            );

            /* 4️⃣ Small stability wait for JS frameworks (React/Angular/Vue etc.) */
            Thread.sleep(1500);


            JavascriptExecutor js = (JavascriptExecutor) driver;

            String script = """
                return Array.from(document.querySelectorAll(
                             'input, textarea, select, button, a[data-target], a[href], [role="button"], [onclick], [tabindex]'
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
                             const dataTarget = el.getAttribute('data-target') || null;
                    
                             const css = id && id.trim() !== '' ? '#' + id : null;
                    
                             const xpath = id && id.trim() !== '' ? '//*[@id="' + id + '"]' : null;
                    
                             return {
                               tag: el.tagName.toLowerCase(),
                               type: el.type || null,
                               id: id,
                               name: name,
                               text: text,
                               css: css,
                               xpath: xpath,
                               dataTarget: dataTarget
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
                e.dataTarget=(String)map.get("dataTarget");
                elements.add(e);
            }


            return elements;

        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    public List<FieldDescriptor> scanCurrentPage(WebDriver driver) {
        System.out.println("Scan Current DOM (no refresh)");

        try {
            WebDriverWait wait = new WebDriverWait(driver, Duration.ofSeconds(15));

            // Wait for page ready (no navigation)
            wait.until(d ->
                    ((JavascriptExecutor) d)
                            .executeScript("return document.readyState")
                            .equals("complete")
            );

            // Small stabilization buffer (important for modals / React)
            Thread.sleep(500);

            return extractElementsFromDom(driver);

        } catch (Exception e) {
            throw new RuntimeException("Failed to scan current DOM", e);
        }
    }

    private List<FieldDescriptor> extractElementsFromDom(WebDriver driver) {

        JavascriptExecutor js = (JavascriptExecutor) driver;

        String script = """
return (function(){
  function visibilityOk(el){
    const rect = el.getBoundingClientRect();
    const style = window.getComputedStyle(el);
    return rect.width > 0 && rect.height > 0 && style.visibility !== 'hidden' && style.display !== 'none' && !el.disabled;
  }

  function makeCssFromAttrs(el){
    if(el.id) return '#'+el.id;
    if(el.name) return el.tagName.toLowerCase() + '[name=\"' + el.name + '\"]';
    const cls = (el.className||'').toString().trim().split(/\\s+/).filter(Boolean);
    if(cls.length){
      return el.tagName.toLowerCase() + '.' + cls.join('.');
    }
    return null;
  }

  function uniqueXPath(el){
    if(el.id) return '//*[@id=\"' + el.id + '\"]';
    // build xpath from tag and index among siblings
    function idx(n){
      let i = 1;
      let sib = n.previousElementSibling;
      while(sib){
        if(sib.tagName === n.tagName) i++;
        sib = sib.previousElementSibling;
      }
      return i;
    }
    let parts = [];
    let node = el;
    while(node && node.nodeType === 1){
      let tag = node.tagName.toLowerCase();
      let i = idx(node);
      parts.unshift(tag + '[' + i + ']');
      node = node.parentElement;
      if(parts.length > 10) break; // safety
    }
    return '/' + parts.join('/');
  }

  const selector = [
    'input',
    'textarea',
    'select',
    'button',
    'a[href]',
    '[role=\"button\"]',
    '[onclick]',
    '[tabindex]',
    '[contenteditable=\"true\"]'
  ].join(',');

  return Array.from(document.querySelectorAll(selector))
    .filter(el => visibilityOk(el))
    .map(function(el){
      const tag = el.tagName.toLowerCase();
      const type = el.getAttribute('type') ? el.getAttribute('type').toLowerCase() : (tag === 'textarea' ? 'textarea' : null);
      const id = el.id || null;
      const name = el.name || null;
      const placeholder = el.placeholder || null;
      const accept = el.getAttribute('accept') || null; // for file inputs
      const disabled = !!el.disabled;
      const dataTarget = el.getAttribute('data-target') || null;
      
      // compute text for selects, buttons, anchors, or innerText for others
      let text = null;
      if(tag === 'select'){
        if(el.selectedIndex >= 0 && el.options.length > 0){
          text = el.options[el.selectedIndex].text.trim();
        } else if(el.options.length > 0){
          text = el.options[0].text.trim();
        }
      } else if(tag === 'button' || (tag === 'a' && (el.innerText||'').trim())){
        text = (el.innerText||'').trim();
      } else if(el.getAttribute('aria-label')){
        text = el.getAttribute('aria-label').trim();
      } else {
        text = (el.innerText||'').trim() || null;
      }

      let css = null;
      if(id) css = '#'+id;
      else css = makeCssFromAttrs(el);

      let xpath = id ? '//*[@id=\"' + id + '\"]' : uniqueXPath(el);

      return {
        tag: tag,
        type: type,
        id: id,
        name: name,
        text: text,
        css: css,
        xpath: xpath,
        placeholder: placeholder,
        accept: accept,
        disabled: disabled,
        dataTarget: dataTarget
      };
    });
})();
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
            e.dataTarget = (String) map.get("dataTarget");

            elements.add(e);
        }

        return elements;
    }
}

