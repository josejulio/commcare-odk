package org.commcare.dalvik.activities;

import android.content.Intent;
import android.content.res.Configuration;
import android.os.Bundle;
import android.util.Pair;
import android.view.View;
import android.view.View.OnClickListener;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.RelativeLayout;

import org.commcare.android.framework.ManagedUi;
import org.commcare.android.framework.SessionAwareCommCareActivity;
import org.commcare.android.framework.UiElement;
import org.commcare.android.logic.DetailCalloutListenerDefaultImpl;
import org.commcare.android.models.AndroidSessionWrapper;
import org.commcare.android.models.Entity;
import org.commcare.android.models.NodeEntityFactory;
import org.commcare.android.util.DetailCalloutListener;
import org.commcare.android.util.SerializationUtil;
import org.commcare.android.util.SessionStateUninitException;
import org.commcare.android.view.TabbedDetailView;
import org.commcare.dalvik.R;
import org.commcare.dalvik.application.CommCareApplication;
import org.commcare.session.CommCareSession;
import org.commcare.session.SessionFrame;
import org.commcare.suite.model.CalloutData;
import org.commcare.suite.model.Detail;
import org.javarosa.core.model.instance.TreeReference;
import org.javarosa.core.services.locale.Localization;

/**
 * @author ctsims
 */
@ManagedUi(R.layout.entity_detail)
public class EntityDetailActivity
        extends SessionAwareCommCareActivity
        implements DetailCalloutListener {

    // reference id of selected element being detailed
    public static final String CONTEXT_REFERENCE = "eda_crid";
    public static final String DETAIL_ID = "eda_detail_id";
    public static final String DETAIL_PERSISTENT_ID = "eda_persistent_id";

    private NodeEntityFactory factory;
    private Pair<Detail, TreeReference> mEntityContext;
    private TreeReference mTreeReference;
    private int detailIndex;

    @UiElement(value = R.id.entity_detail)
    private RelativeLayout container;

    @UiElement(value = R.id.entity_select_button, locale = "select.detail.confirm")
    private Button next;

    @UiElement(value = R.id.entity_detail_tabs)
    private TabbedDetailView mDetailView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        Intent i = getIntent();

        AndroidSessionWrapper asw;
        CommCareSession session;
        try {
            asw = CommCareApplication._().getCurrentSessionWrapper();
            session = asw.getSession();
        } catch (SessionStateUninitException sue) {
            // The user isn't logged in! bounce this back to where we came from
            this.setResult(RESULT_CANCELED, this.getIntent());
            this.finish();
            return;
        }
        String passedCommand = getIntent().getStringExtra(SessionFrame.STATE_COMMAND_ID);

        // Is the detail screen for showing entities, without option for moving
        // forward on to form manipulation?
        boolean viewMode;
        if (passedCommand != null) {
            viewMode = session.isViewCommand(passedCommand);
        } else {
            viewMode = session.isViewCommand(session.getCommand());
        }

        factory =
                new NodeEntityFactory(session.getDetail(getIntent().getStringExtra(EntityDetailActivity.DETAIL_ID)),
                        asw.getEvaluationContext());

        mTreeReference =
                SerializationUtil.deserializeFromIntent(getIntent(),
                        EntityDetailActivity.CONTEXT_REFERENCE,
                        TreeReference.class);
        String shortDetailId = getIntent().getStringExtra(EntityDetailActivity.DETAIL_PERSISTENT_ID);
        if (shortDetailId != null) {
            Detail shortDetail = session.getDetail(shortDetailId);
            this.mEntityContext = new Pair<Detail, TreeReference>(shortDetail, mTreeReference);
        }

        super.onCreate(savedInstanceState);
        
        /* Caution: The detailIndex field comes from EntitySelectActivity, which is the 
         * source of this intent. In some instances, the detailIndex may not have been assigned,
         * in which case it will take on a value of -1. If making use of the detailIndex, it may
         * be useful to include the debugging print statement below.
         */
        this.detailIndex = i.getIntExtra("entity_detail_index", -1);
        //if (detailIndex == -1) { System.out.println("WARNING: detailIndex not assigned from intent"); }

        if (this.getString(R.string.panes).equals("two")) {
            if (getResources().getConfiguration().orientation == Configuration.ORIENTATION_LANDSCAPE) {
                //this occurs when the screen was rotated to be vertical on the select activity. We
                //want to navigate back to that screen now.
                this.setResult(RESULT_CANCELED, this.getIntent());
                this.finish();
                return;
            }
        }

        next.setOnClickListener(new OnClickListener() {
            public void onClick(View v) {
                select();
            }
        });

        if (viewMode) {
            next.setCompoundDrawablesWithIntrinsicBounds(0, 0, 0, 0);
            next.setText(Localization.get("select.detail.bypass"));
        }

        mDetailView.setRoot((ViewGroup)container.findViewById(R.id.entity_detail_tabs));
        mDetailView.refresh(factory.getDetail(), mTreeReference, detailIndex, true);

        mDetailView.setDetail(factory.getDetail());
    }

    public Pair<Detail, TreeReference> requestEntityContext() {
        return mEntityContext;
    }

    @Override
    protected boolean isTopNavEnabled() {
        return true;
    }

    @Override
    public String getActivityTitle() {
        return null;
    }

    private void loadOutgoingIntent(Intent i) {
        i.putExtra(SessionFrame.STATE_DATUM_VAL, this.getIntent().getStringExtra(SessionFrame.STATE_DATUM_VAL));
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent intent) {
        switch (requestCode) {
            case DetailCalloutListenerDefaultImpl.CALL_OUT:
                if (resultCode == RESULT_CANCELED) {
                    mDetailView.refresh(factory.getDetail(), mTreeReference, detailIndex, true);
                    return;
                } else {
                    long duration = intent.getLongExtra(CallOutActivity.CALL_DURATION, 0);

                    Intent i = new Intent(EntityDetailActivity.this.getIntent());
                    loadOutgoingIntent(i);
                    i.putExtra(CallOutActivity.CALL_DURATION, duration);
                    setResult(RESULT_OK, i);

                    finish();
                    return;
                }
            default:
                super.onActivityResult(requestCode, resultCode, intent);
        }
    }

    @Override
    public void callRequested(String phoneNumber) {
        DetailCalloutListenerDefaultImpl.callRequested(this, phoneNumber);
    }

    @Override
    public void addressRequested(String address) {
        DetailCalloutListenerDefaultImpl.addressRequested(this, address);
    }

    @Override
    public void playVideo(String videoRef) {
        DetailCalloutListenerDefaultImpl.playVideo(this, videoRef);
    }

    @Override
    public void performCallout(CalloutData callout, int id) {
        DetailCalloutListenerDefaultImpl.performCallout(this, callout, id);
    }

    @Override
    protected boolean onForwardSwipe() {
        // Move along, provided we're on the last tab of tabbed case details
        if (mDetailView.getCurrentTab() >= mDetailView.getTabCount() - 1) {
            select();
            return true;
        }
        return false;
    }

    @Override
    protected boolean onBackwardSwipe() {
        // Move back, provided we're on the first screen of tabbed case details
        if (mDetailView.getCurrentTab() < 1) {
            finish();
            return true;
        }
        return false;
    }

    /**
     * Move along to form entry.
     */
    private void select() {
        Intent i = new Intent(EntityDetailActivity.this.getIntent());
        loadOutgoingIntent(i);
        setResult(RESULT_OK, i);
        finish();
    }
}
