package input;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.Input.Buttons;
import com.badlogic.gdx.Input.Keys;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.JsonReader;
import com.badlogic.gdx.utils.JsonValue;

import java.util.EnumMap;
import java.util.Map;

/** Action-to-input mapping with defaults, optionally overridden from a controls JSON file. */
public class Bindings {
    private final Map<InputAction, Integer> map = new EnumMap<>(InputAction.class);

    public Bindings() {
        map.put(InputAction.SELECT, Buttons.LEFT);
        map.put(InputAction.COMMAND, Buttons.RIGHT);
        map.put(InputAction.CAMERA_DRAG, Buttons.MIDDLE);
        map.put(InputAction.ADD_TO_SELECTION, Keys.SHIFT_LEFT);
        map.put(InputAction.PAUSE, Keys.SPACE);
    }

    public int code(InputAction action) {
        Integer value = map.get(action);
        return value == null ? -1 : value;
    }

    public void load(FileHandle file) {
        if (file == null || !file.exists()) {
            return;
        }
        JsonValue root = new JsonReader().parse(file);
        readButtons(root.get("buttons"));
        readKeys(root.get("keys"));
        readKeys(root.get("modifiers"));
    }

    private void readButtons(JsonValue section) {
        if (section == null) {
            return;
        }
        for (JsonValue entry = section.child; entry != null; entry = entry.next) {
            InputAction action = parseAction(entry.name());
            int code = parseButton(entry.asString());
            if (action != null && code >= 0) {
                map.put(action, code);
            }
        }
    }

    private void readKeys(JsonValue section) {
        if (section == null) {
            return;
        }
        for (JsonValue entry = section.child; entry != null; entry = entry.next) {
            InputAction action = parseAction(entry.name());
            int code = Keys.valueOf(entry.asString());
            if (action != null && code != -1) {
                map.put(action, code);
            }
        }
    }

    private InputAction parseAction(String name) {
        try {
            return InputAction.valueOf(name);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private int parseButton(String name) {
        switch (name.toUpperCase()) {
            case "LEFT": return Buttons.LEFT;
            case "RIGHT": return Buttons.RIGHT;
            case "MIDDLE": return Buttons.MIDDLE;
            case "BACK": return Buttons.BACK;
            case "FORWARD": return Buttons.FORWARD;
            default: return -1;
        }
    }
}
