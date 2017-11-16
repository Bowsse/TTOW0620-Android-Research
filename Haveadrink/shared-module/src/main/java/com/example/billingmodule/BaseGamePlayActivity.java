/*
 * Copyright 2017 Google Inc. All Rights Reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.billingmodule;

import static com.android.billingclient.api.BillingClient.BillingResponse;
import static com.example.billingmodule.billing.BillingManager.BILLING_MANAGER_NOT_INITIALIZED;

import android.app.AlertDialog;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.DrawableRes;
import android.support.annotation.Nullable;
import android.support.annotation.StringRes;
import android.support.annotation.UiThread;
import android.support.annotation.VisibleForTesting;
import android.support.v4.app.DialogFragment;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.util.Log;
import android.view.View;
import android.widget.ImageView;
import com.example.billingmodule.billing.BillingManager;
import com.example.billingmodule.billing.BillingProvider;
import com.example.billingmodule.skulist.AcquireFragment;

/**
 * Example game using in-app billing version 3.
 */
public abstract class BaseGamePlayActivity extends FragmentActivity implements BillingProvider {
    // Debug tag, for logging
    private static final String TAG = "BaseGamePlayActivity";

    // Tag for a dialog that allows us to find it when screen was rotated
    private static final String DIALOG_TAG = "dialog";

    // Default sample's package name to check if you changed it
    private static final String DEFAULT_PACKAGE_PREFIX = "com.example";

    private BillingManager mBillingManager;
    private AcquireFragment mAcquireFragment;
    private MainViewController mViewController;

    private View mScreenWait, mScreenMain;
    private ImageView mTitleImageView, mBeerImageView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(getLayoutResId());

        // Start the controller and load game data
        mViewController = new MainViewController(this);

        if (getPackageName().startsWith(DEFAULT_PACKAGE_PREFIX)) {
            throw new RuntimeException("Please change the sample's package name!");
        }

        // Try to restore dialog fragment if we were showing it prior to screen rotation
        if (savedInstanceState != null) {
            mAcquireFragment = (AcquireFragment) getSupportFragmentManager()
                    .findFragmentByTag(DIALOG_TAG);
        }

        // Create and initialize BillingManager which talks to BillingLibrary
        mBillingManager = new BillingManager(this, mViewController.getUpdateListener());

        mScreenWait = findViewById(R.id.screen_wait);
        mScreenMain = findViewById(R.id.screen_main);
        mBeerImageView = ((ImageView) findViewById(R.id.beer_gauge));

        // Specify purchase and drive buttons listeners
        // Note: This couldn't be done inside *.xml for Android TV since TV layout is inflated
        // via AppCompat
        findViewById(R.id.button_purchase).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onPurchaseButtonClicked(view);
            }
        });
        findViewById(R.id.button_drive).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                onDriveButtonClicked(view);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // Note: We query purchases in onResume() to handle purchases completed while the activity
        // is inactive. For example, this can happen if the activity is destroyed during the
        // purchase flow. This ensures that when the activity is resumed it reflects the user's
        // current purchases.
        if (mBillingManager != null
                && mBillingManager.getBillingClientResponseCode() == BillingResponse.OK) {
            mBillingManager.queryPurchases();
        }
    }

    @Override
    public BillingManager getBillingManager() {
        return mBillingManager;
    }

    @Override
    public boolean isGoldMonthlySubscribed() {
        return mViewController.isGoldMonthlySubscribed();
    }

    @Override
    public boolean isGoldYearlySubscribed() {
        return mViewController.isGoldYearlySubscribed();
    }

    @Override
    public boolean isTankFull() {
        return mViewController.isTankFull();
    }

    protected abstract int getLayoutResId();

    /**
     * User clicked the "Buy Gas" button - show a purchase dialog with all available SKUs
     */
    public void onPurchaseButtonClicked(final View arg0) {
        Log.d(TAG, "Purchase button clicked.");

        if (mAcquireFragment == null) {
            mAcquireFragment = new AcquireFragment();
        }

        if (!isAcquireFragmentShown()) {
            mAcquireFragment.show(getSupportFragmentManager(), DIALOG_TAG);

            if (mBillingManager != null
                    && mBillingManager.getBillingClientResponseCode()
                            > BILLING_MANAGER_NOT_INITIALIZED) {
                mAcquireFragment.onManagerReady(this);
            }
        }
    }

    /**
     * Drive button clicked. Burn gas!
     */
    public void onDriveButtonClicked(View arg0) {
        Log.d(TAG, "Drive button clicked.");

        if (mViewController.isTankEmpty()) {
            alert(R.string.alert_no_gas);
        } else {
            mViewController.useGas();
            alert(R.string.alert_drove);
            updateUi();
        }
    }

    @Override
    public void onDestroy() {
        Log.d(TAG, "Destroying helper.");
        if (mBillingManager != null) {
            mBillingManager.destroy();
        }
        super.onDestroy();
    }

    /**
     * Remove loading spinner and refresh the UI
     */
    public void showRefreshedUi() {
        setWaitScreen(false);
        updateUi();
        if (mAcquireFragment != null) {
            mAcquireFragment.refreshUI();
        }
    }

    /**
     * Show an alert dialog to the user
     * @param messageId String id to display inside the alert dialog
     */
    @UiThread
    void alert(@StringRes int messageId) {
        alert(messageId, null);
    }

    /**
     * Show an alert dialog to the user
     * @param messageId String id to display inside the alert dialog
     * @param optionalParam Optional attribute for the string
     */
    @UiThread
    void alert(@StringRes int messageId, @Nullable Object optionalParam) {
        if (Looper.getMainLooper().getThread() != Thread.currentThread()) {
            throw new RuntimeException("Dialog could be shown only from the main thread");
        }

        AlertDialog.Builder bld = new AlertDialog.Builder(this);
        bld.setNeutralButton("OK", null);

        if (optionalParam == null) {
            bld.setMessage(messageId);
        } else {
            bld.setMessage(getResources().getString(messageId, optionalParam));
        }

        bld.create().show();
    }

    void onBillingManagerSetupFinished() {
        if (mAcquireFragment != null) {
            mAcquireFragment.onManagerReady(this);
        }
    }

    @VisibleForTesting public MainViewController getViewController() {
        return mViewController;
    }

    /**
     * Enables or disables the "please wait" screen.
     */
    private void setWaitScreen(boolean set) {
        mScreenMain.setVisibility(set ? View.GONE : View.VISIBLE);
        mScreenWait.setVisibility(set ? View.VISIBLE : View.GONE);
    }

    /**
     * Sets image resource and also adds a tag to be able to verify that image is correct in tests
     */
    private void setImageResourceWithTestTag(ImageView imageView, @DrawableRes int resId) {
        imageView.setImageResource(resId);
        imageView.setTag(resId);
    }

    /**
     * Update UI to reflect model
     */
    @UiThread
    private void updateUi() {
        Log.d(TAG, "Updating the UI. Thread: " + Thread.currentThread().getName());

        // Update gas gauge to reflect tank status
        setImageResourceWithTestTag(mBeerImageView, mViewController.getTankResId());

        if (isGoldMonthlySubscribed() || isGoldYearlySubscribed()) {
            mTitleImageView.setBackgroundColor(ContextCompat.getColor(this, R.color.gold));
        }
    }

    public boolean isAcquireFragmentShown() {
        return mAcquireFragment != null && mAcquireFragment.isVisible();
    }

    public DialogFragment getDialogFragment() {
        return mAcquireFragment;
    }
}
