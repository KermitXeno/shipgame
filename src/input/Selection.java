package input;

import model.crew.Crew;

import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.Set;

/** The current set of selected crew, kept in insertion order. */
public class Selection {
    private final Set<Crew> selected = new LinkedHashSet<>();

    public Collection<Crew> getSelected() {
        return selected;
    }

    public boolean isEmpty() {
        return selected.isEmpty();
    }

    public boolean isSelected(Crew crew) {
        return selected.contains(crew);
    }

    public void clear() {
        selected.clear();
    }

    public void select(Crew crew) {
        selected.clear();
        if (crew != null) {
            selected.add(crew);
        }
    }

    public void add(Crew crew) {
        if (crew != null) {
            selected.add(crew);
        }
    }

    public void toggle(Crew crew) {
        if (crew == null) {
            return;
        }
        if (!selected.remove(crew)) {
            selected.add(crew);
        }
    }
}
