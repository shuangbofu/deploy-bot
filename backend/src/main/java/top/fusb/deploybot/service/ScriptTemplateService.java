package top.fusb.deploybot.service;

import org.springframework.stereotype.Service;

import java.util.Map;

@Service
public class ScriptTemplateService {

    public String render(String template, Map<String, String> variables) {
        String rendered = template;
        for (Map.Entry<String, String> entry : variables.entrySet()) {
            rendered = rendered.replace("{{" + entry.getKey() + "}}", entry.getValue() == null ? "" : entry.getValue());
        }
        return rendered;
    }
}
