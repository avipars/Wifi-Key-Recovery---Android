/**
 * ****************************************************************************
 * Copyright 2011 Alexandros Schillings
 * <p/>
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * <p/>
 * http://www.apache.org/licenses/LICENSE-2.0
 * <p/>
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * ****************************************************************************
 */
package aws.apps.wifiKeyRecovery.activities.main;

import android.app.AlertDialog;
import android.app.Dialog;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.DialogInterface.OnDismissListener;
import android.content.Intent;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.os.Parcelable;
import android.support.v7.app.ActionBarActivity;
import android.text.ClipboardManager;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Log;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.Date;
import java.util.List;

import aws.apps.wifiKeyRecovery.BuildConfig;
import aws.apps.wifiKeyRecovery.R;
import aws.apps.wifiKeyRecovery.activities.export.ExportActivity;
import aws.apps.wifiKeyRecovery.activities.details.WifiDetailsActivity;
import aws.apps.wifiKeyRecovery.containers.SavedData;
import aws.apps.wifiKeyRecovery.activities.main.IconFriendlyPopupMenu.OnMenuItemClickListener;
import aws.apps.wifiKeyRecovery.ui.MyAlertBox;
import aws.apps.wifiKeyRecovery.util.ExecTerminal;
import aws.apps.wifiKeyRecovery.util.ExecuteThread;
import aws.apps.wifiKeyRecovery.util.UsefulBits;
import uk.co.alt236.wifipasswordaccess.container.WifiNetworkInfo;
import uk.co.alt236.wifipasswordaccess.container.WifiProtectedNetworkInfo;

@SuppressWarnings("deprecation")
public class MainActivity extends ActionBarActivity implements OnItemClickListener, OnMenuItemClickListener {
    private static final int DIALOG_GET_PASSWORDS = 1;
    private final String TAG = this.getClass().getName();
    private Bundle mThreadBundle;
    private EditText mEditFilter;
    private ExecuteThread mExecuteThread;
    private ListView mList;
    private WifiNetworkInfo mCurrentNetinfo;
    private NetInfoAdapter mNiAdapter;
    private final TextWatcher filterTextWatcher = new TextWatcher() {

        @Override
        public void afterTextChanged(final Editable s) {
        }

        @Override
        public void beforeTextChanged(final CharSequence s, final int start, final int count, final int after) {
        }

        @Override
        public void onTextChanged(final CharSequence s, final int start, final int before, final int count) {
            if (mNiAdapter != null) {
                mNiAdapter.getFilter().filter(s);
            } else {
                Log.w(TAG, "^ TextWatcher: Adapter is null!");
            }
        }
    };
    private String mTimeDate = "";
    private TextView mTextViewResultCount;
    private final Handler handler = new Handler() {
        @Override
        @SuppressWarnings("unchecked")
        public void handleMessage(final Message msg) {
            switch (msg.what) {

                case ExecuteThread.WORK_COMPLETED:
                    Log.d(TAG, "^ Worker Thread: WORK_COMPLETED");

                    final List<WifiNetworkInfo> list = (ArrayList<WifiNetworkInfo>) msg.getData().getSerializable("passwords");

                    if (list != null) {
                        Collections.sort(list, new NetInfoComparator());
                        populateList(list);
                        mList.setTag(list);
                    }

                    mExecuteThread.setState(ExecuteThread.STATE_DONE);
                    removeDialog(DIALOG_GET_PASSWORDS);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);

                case ExecuteThread.WORK_INTERUPTED:
                    mExecuteThread.setState(ExecuteThread.STATE_DONE);
                    removeDialog(DIALOG_GET_PASSWORDS);
                    setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED);
                    break;
            }

        }
    };
    private UsefulBits mUsefulBits;

    // Sets screen rotation as fixed to current rotation setting
    private void LockScreenRotation() {
        // Stop the screen orientation changing during an event
        switch (this.getResources().getConfiguration().orientation) {
            case Configuration.ORIENTATION_PORTRAIT:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_PORTRAIT);
                break;
            case Configuration.ORIENTATION_LANDSCAPE:
                this.setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE);
                break;
        }
    }

    private void copyStringToClipboard(final String text) {
        if (text.length() > 0) {
            String msgtext = "";
            if (text.length() > 150) {
                msgtext = text.substring(0, 150) + "...";
            } else {
                msgtext = text;
            }

            final String message = "'" + msgtext + "' " + getString(R.string.text_copied);
            mUsefulBits.showToast(message, Toast.LENGTH_SHORT, Gravity.TOP, 0, 0);

            final ClipboardManager ClipMan = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
            ClipMan.setText(text);
        }
    }

    private void getPasswords() {
        LockScreenRotation();
        final boolean hasRoot;

        if (BuildConfig.USE_DEBUG_DATA) {
            hasRoot = true;
        } else {
            final ExecTerminal et = new ExecTerminal();
            hasRoot = et.checkSu();
        }

        if (hasRoot) {
            showDialog(DIALOG_GET_PASSWORDS);
        } else {
            final AlertDialog dlg = MyAlertBox.create(
                    this, getString(R.string.root_needed),
                    getString(R.string.app_name),
                    getString(android.R.string.ok));

            dlg.setOnDismissListener(new OnDismissListener() {

                @Override
                public void onDismiss(final DialogInterface dialog) {
                    MainActivity.this.finish();
                }
            });

            dlg.show();
        }
    }

    public void onClearSearchClick(final View v) {
        mEditFilter.setText("");
    }

    /**
     * Called when the activity is first created.
     */
    @Override
    public void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mUsefulBits = new UsefulBits(this);

        // setup GUI
        mEditFilter = (EditText) findViewById(R.id.edit_search);
        mList = (ListView) findViewById(R.id.list);
        mTextViewResultCount = (TextView) findViewById(R.id.tvResults);

        mList.setFastScrollEnabled(true);
        mList.setOnItemClickListener(this);
        mList.setFastScrollEnabled(true);
        populateInfo();
    }

    @Override
    protected Dialog onCreateDialog(final int id) {
        switch (id) {
            case DIALOG_GET_PASSWORDS:
                final ProgressDialog mExecuteDialog = new ProgressDialog(this);
                mExecuteDialog.setMessage(getString(R.string.dialogue_text_please_wait));

                mExecuteThread = new ExecuteThread(handler, this, mThreadBundle);
                mExecuteThread.start();
                return mExecuteDialog;
            default:
                return null;
        }
    }

    /**
     * Creates the menu items
     */
    @Override
    public boolean onCreateOptionsMenu(final Menu menu) {
        new MenuInflater(this).inflate(R.menu.menu_home, menu);
        return true;
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        mEditFilter.removeTextChangedListener(filterTextWatcher);
    }

    @Override
    public void onItemClick(final AdapterView<?> l, final View v, final int position, final long id) {

        final WifiNetworkInfo networkInfo = (WifiNetworkInfo) l.getAdapter().getItem(position);
        if (networkInfo != null) {
            final Intent intent = new Intent(this, WifiDetailsActivity.class);
            //noinspection RedundantCast
            intent.putExtra(
                    WifiDetailsActivity.EXTRAS_NETWORK_INFO, (Parcelable) networkInfo);
            startActivity(intent);
        }
    }

    @Override
    public boolean onMenuItemClick(final MenuItem paramMenuItem) {
        final int actionId = paramMenuItem.getItemId();
        String text;

        final boolean res;
        switch (actionId) {
            case PopupMenuActionHelper.ACTION_ID_NETWORK_COPY_ALL_AS_TEXT:
                copyStringToClipboard(mCurrentNetinfo.toString());
                res = true;
                break;
            case PopupMenuActionHelper.ACTION_ID_NETWORK_COPY_PASSWORD:
                if(mCurrentNetinfo instanceof WifiProtectedNetworkInfo){
                    copyStringToClipboard(((WifiProtectedNetworkInfo) mCurrentNetinfo).getPassword());
                } else {
                    Toast.makeText(this, "This network is not protected!", Toast.LENGTH_SHORT).show();
                }

                res = true;
                break;
            case PopupMenuActionHelper.ACTION_ID_NETWORK_SHOW_QRCODE:
                res = true;
                break;
            default:
                res = false;
        }

        return res;
    }

    /**
     * Handles item selections
     */
    @SuppressWarnings("unchecked")
    @Override
    public boolean onOptionsItemSelected(final MenuItem item) {
        switch (item.getItemId()) {
            case R.id.menu_about:
                mUsefulBits.showAboutDialogue();
                return true;
            case R.id.menu_export:
                final Intent myIntent = new Intent();
                String export_text = "";
                export_text += getString(R.string.label_wifi_passwords) + "\n";
                export_text += listToString((List<WifiNetworkInfo>) mList.getTag()) + "\n\n";
                export_text += mTextViewResultCount.getText();
                myIntent.putExtra("info", export_text);
                myIntent.putExtra("time", mTimeDate);
                myIntent.setClass(this, ExportActivity.class);
                startActivity(myIntent);
                return true;
            case R.id.menu_refresh:
                refreshInfo();
                return true;
        }
        return false;
    }

    @Override
    protected void onPause() {
        super.onPause();
        mEditFilter.removeTextChangedListener(filterTextWatcher);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (mEditFilter != null) {
            mEditFilter.addTextChangedListener(filterTextWatcher);
        }
    }

    @SuppressWarnings("unchecked")
    @Override
    public Object onRetainCustomNonConfigurationInstance() {
        Log.d(TAG, "^ onRetainNonConfigurationInstance()");

        final SavedData saved = new SavedData();

        if (mList.getTag() != null) {
            saved.setWiFiPasswordList((List<WifiNetworkInfo>) mList.getTag());
        }

        saved.setDateTime(mTimeDate);
        return saved;
    }

    /**
     * Retrieves and displays info
     */
    private void populateInfo() {
        final Object data = getLastCustomNonConfigurationInstance();

        if (data == null) { // We need to do everything from scratch!
            mTimeDate = mUsefulBits.formatDateTime("yyyy-MM-dd-HHmmssZ", new Date());
            getPasswords();
        } else {
            final SavedData saved = (SavedData) data;
            mTimeDate = saved.getDateTime();

            populateList(saved.getWifiPasswordList());
            mList.setTag(saved.getWifiPasswordList());
        }
    }

    private void populateList(final List<WifiNetworkInfo> l) {
        if (l.size() > 0) {
            findViewById(R.id.filter_segment).setVisibility(View.VISIBLE);
            mNiAdapter = new NetInfoAdapter(this, l);
            mTextViewResultCount.setText(String.valueOf(l.size()));
            mList.setAdapter(mNiAdapter);
            mEditFilter.addTextChangedListener(filterTextWatcher);
        } else {
            mTextViewResultCount.setText("0");
            findViewById(R.id.filter_segment).setVisibility(View.GONE);
        }
    }

    /**
     * Convenience function combining clearInfo and getInfo
     */
    private void refreshInfo() {
        populateInfo();
    }

    public static String listToString(final List<WifiNetworkInfo> list) {
        final StringBuilder sb = new StringBuilder();

        int cnt = 0;
        for (final WifiNetworkInfo obj : list) {
            cnt += 1;
            sb.append("#" + cnt + ":\n");
            sb.append(obj.getSsid() + "\n");
        }

        return sb.toString();
    }

    private class NetInfoComparator implements Comparator<WifiNetworkInfo> {
        @Override
        public int compare(final WifiNetworkInfo o1, final WifiNetworkInfo o2) {
            return o1.getSsid().compareToIgnoreCase(o2.getSsid());
        }
    }
}