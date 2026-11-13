package com.freecam.camera.databinding;
import com.freecam.camera.R;
import com.freecam.camera.BR;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import android.view.View;
@SuppressWarnings("unchecked")
public class ActivityMainBindingImpl extends ActivityMainBinding  {

    @Nullable
    private static final androidx.databinding.ViewDataBinding.IncludedLayouts sIncludes;
    @Nullable
    private static final android.util.SparseIntArray sViewsWithIds;
    static {
        sIncludes = null;
        sViewsWithIds = new android.util.SparseIntArray();
        sViewsWithIds.put(R.id.tvStatus, 1);
        sViewsWithIds.put(R.id.btnStartStop, 2);
        sViewsWithIds.put(R.id.tvStreamAddress, 3);
        sViewsWithIds.put(R.id.tvUptime, 4);
        sViewsWithIds.put(R.id.tvResolutionFpsBitrate, 5);
        sViewsWithIds.put(R.id.tvBattery, 6);
        sViewsWithIds.put(R.id.tvStorageFree, 7);
        sViewsWithIds.put(R.id.tvStorageUsed, 8);
        sViewsWithIds.put(R.id.tvEventsToday, 9);
        sViewsWithIds.put(R.id.tvMotion, 10);
        sViewsWithIds.put(R.id.switchPreview, 11);
        sViewsWithIds.put(R.id.btnSettings, 12);
        sViewsWithIds.put(R.id.btnPairing, 13);
        sViewsWithIds.put(R.id.btnLogs, 14);
        sViewsWithIds.put(R.id.btnDimScreen, 15);
    }
    // views
    @NonNull
    private final androidx.constraintlayout.widget.ConstraintLayout mboundView0;
    // variables
    // values
    // listeners
    // Inverse Binding Event Handlers

    public ActivityMainBindingImpl(@Nullable androidx.databinding.DataBindingComponent bindingComponent, @NonNull View root) {
        this(bindingComponent, root, mapBindings(bindingComponent, root, 16, sIncludes, sViewsWithIds));
    }
    private ActivityMainBindingImpl(androidx.databinding.DataBindingComponent bindingComponent, View root, Object[] bindings) {
        super(bindingComponent, root, 0
            , (android.widget.Button) bindings[15]
            , (android.widget.Button) bindings[14]
            , (android.widget.Button) bindings[13]
            , (android.widget.Button) bindings[12]
            , (android.widget.Button) bindings[2]
            , (androidx.appcompat.widget.SwitchCompat) bindings[11]
            , (android.widget.TextView) bindings[6]
            , (android.widget.TextView) bindings[9]
            , (android.widget.TextView) bindings[10]
            , (android.widget.TextView) bindings[5]
            , (android.widget.TextView) bindings[1]
            , (android.widget.TextView) bindings[7]
            , (android.widget.TextView) bindings[8]
            , (android.widget.TextView) bindings[3]
            , (android.widget.TextView) bindings[4]
            );
        this.mboundView0 = (androidx.constraintlayout.widget.ConstraintLayout) bindings[0];
        this.mboundView0.setTag(null);
        setRootTag(root);
        // listeners
        invalidateAll();
    }

    @Override
    public void invalidateAll() {
        synchronized(this) {
                mDirtyFlags = 0x1L;
        }
        requestRebind();
    }

    @Override
    public boolean hasPendingBindings() {
        synchronized(this) {
            if (mDirtyFlags != 0) {
                return true;
            }
        }
        return false;
    }

    @Override
    public boolean setVariable(int variableId, @Nullable Object variable)  {
        boolean variableSet = true;
            return variableSet;
    }

    @Override
    protected boolean onFieldChange(int localFieldId, Object object, int fieldId) {
        switch (localFieldId) {
        }
        return false;
    }

    @Override
    protected void executeBindings() {
        long dirtyFlags = 0;
        synchronized(this) {
            dirtyFlags = mDirtyFlags;
            mDirtyFlags = 0;
        }
        // batch finished
    }
    // Listener Stub Implementations
    // callback impls
    // dirty flag
    private  long mDirtyFlags = 0xffffffffffffffffL;
    /* flag mapping
        flag 0 (0x1L): null
    flag mapping end*/
    //end
}