package org.commcare.android.adapters;

import android.content.Context;
import android.database.DataSetObserver;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListAdapter;

import org.commcare.android.framework.ModifiableEntityDetailAdapter;
import org.commcare.android.models.Entity;
import org.commcare.android.view.EntityView;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;

import java.util.List;
import java.util.ArrayList;

/**
 * Created by jschweers on 8/24/2015.
 * <p/>
 * Adapter for taking a nodeset, contextualizing it against an entity,
 * and then displaying one item for each node in the resulting set.
 */
public class EntitySubnodeDetailAdapter implements ListAdapter, ModifiableEntityDetailAdapter {

    private Context context;
    private Detail detail;
    private List<TreeReference> references;
    private List<Entity<TreeReference>> entities;
    private ListItemViewModifier modifier;

    public EntitySubnodeDetailAdapter(Context context, Detail detail, List<TreeReference> references, List<Entity<TreeReference>> entities, ListItemViewModifier modifier) {
        this.context = context;
        this.detail = detail;
        this.modifier = modifier;
        this.initializeData(references, entities);
    }

    /**
     * Populate entities and references. Will ignore any entities (and their
     * associated references) without any valid fields.
     */
    private void initializeData(List<TreeReference> referenceList, List<Entity<TreeReference>> entityList) {
        this.references = new ArrayList<>(referenceList.size());
        this.entities = new ArrayList<>(entityList.size());
        int entityIndex = 0;
        for (Entity e : entityList) {
            for (int i = 0; i < e.getNumFields(); i++) {
                if (e.isValidField(i)) {
                    this.entities.add(e);
                    this.references.add(referenceList.get(entityIndex));
                    break;
                }
            }
            entityIndex++;
        }
    }

    @Override
    public boolean areAllItemsEnabled() {
        return false;
    }

    @Override
    public boolean isEnabled(int position) {
        return false;
    }

    @Override
    public int getCount() {
        return references.size();
    }

    @Override
    public Object getItem(int position) {
        return references.get(position);
    }

    @Override
    public long getItemId(int position) {
        return position;
    }

    @Override
    public int getItemViewType(int position) {
        return 0;
    }

    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        EntityView view = (EntityView)convertView;
        Entity entity = this.entities.get(position);
        if (view == null) {
            view = new EntityView(context, detail, entity, null, null, position, false);
        } else {
            view.refreshViewsForNewEntity(entity, false, position);
        }
        if (modifier != null) {
            modifier.modify(view, position);
        }
        return view;
    }

    @Override
    public int getViewTypeCount() {
        return 1;
    }

    @Override
    public boolean hasStableIds() {
        return true;
    }

    @Override
    public boolean isEmpty() {
        return getCount() > 0;
    }

    @Override
    public void registerDataSetObserver(DataSetObserver observer) {
    }

    @Override
    public void unregisterDataSetObserver(DataSetObserver observer) {
    }

    @Override
    public void setModifier(ListItemViewModifier modifier) {
        this.modifier = modifier;
    }
}
