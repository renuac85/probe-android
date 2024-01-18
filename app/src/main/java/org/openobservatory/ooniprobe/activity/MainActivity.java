package org.openobservatory.ooniprobe.activity;

import static org.openobservatory.ooniprobe.common.service.RunTestService.CHANNEL_ID;

import android.Manifest;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;

import android.view.View;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;
import androidx.lifecycle.Observer;
import androidx.work.Constraints;
import androidx.work.ExistingPeriodicWorkPolicy;
import androidx.work.ExistingWorkPolicy;
import androidx.work.NetworkType;
import androidx.work.OneTimeWorkRequest;
import androidx.work.PeriodicWorkRequest;
import androidx.work.WorkInfo;
import androidx.work.WorkManager;
import androidx.work.WorkRequest;

import com.google.android.material.snackbar.Snackbar;
import com.google.common.base.Optional;

import org.openobservatory.engine.OONIRunDescriptor;
import org.openobservatory.ooniprobe.R;
import org.openobservatory.ooniprobe.activity.adddescriptor.AddDescriptorActivity;
import org.openobservatory.ooniprobe.common.*;
import org.openobservatory.ooniprobe.common.service.ServiceUtil;
import org.openobservatory.ooniprobe.common.worker.AutoUpdateDescriptorsWorker;
import org.openobservatory.ooniprobe.common.worker.ManualUpdateDescriptorsWorker;
import org.openobservatory.ooniprobe.databinding.ActivityMainBinding;
import org.openobservatory.ooniprobe.domain.UpdatesNotificationManager;
import org.openobservatory.ooniprobe.fragment.DashboardFragment;
import org.openobservatory.ooniprobe.fragment.PreferenceGlobalFragment;
import org.openobservatory.ooniprobe.fragment.ResultListFragment;
import org.openobservatory.ooniprobe.fragment.dynamicprogressbar.OONIRunDynamicProgressBar;
import org.openobservatory.ooniprobe.fragment.dynamicprogressbar.OnActionListener;
import org.openobservatory.ooniprobe.fragment.dynamicprogressbar.ProgressType;
import org.openobservatory.ooniprobe.model.database.TestDescriptor;

import java.io.Serializable;
import java.util.concurrent.TimeUnit;

import javax.inject.Inject;

import kotlin.Unit;
import localhost.toolkit.app.fragment.ConfirmDialogFragment;

public class MainActivity extends AbstractActivity implements ConfirmDialogFragment.OnConfirmedListener {
    private static final String RES_ITEM = "resItem";
    private static final String RES_SNACKBAR_MESSAGE = "resSnackbarMessage";
    public static final String NOTIFICATION_DIALOG = "notification";
    public static final String AUTOTEST_DIALOG = "automatic_testing";
    public static final String BATTERY_DIALOG = "battery_optimization";

    private ActivityMainBinding binding;

    @Inject
    UpdatesNotificationManager notificationManager;

    @Inject
    PreferenceManager preferenceManager;

    @Inject
    TestDescriptorManager descriptorManager;

    private ActivityResultLauncher<String> requestPermissionLauncher;

    public static Intent newIntent(Context context, int resItem) {
        return new Intent(context, MainActivity.class).putExtra(RES_ITEM, resItem).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    public static Intent newIntent(Context context, int resItem, String message) {
        return new Intent(context, MainActivity.class)
                .putExtra(RES_ITEM, resItem)
                .putExtra(RES_SNACKBAR_MESSAGE, message)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
    }

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        getActivityComponent().inject(this);
        if (preferenceManager.isShowOnboarding()) {
            startActivity(new Intent(MainActivity.this, OnboardingActivity.class));
            finish();
        } else {
            binding = ActivityMainBinding.inflate(getLayoutInflater());
            setContentView(binding.getRoot());
            binding.bottomNavigation.setOnItemSelectedListener(item -> {
                switch (item.getItemId()) {
                    case R.id.dashboard:
                        getSupportFragmentManager().beginTransaction().replace(R.id.content, new DashboardFragment()).commit();
                        return true;
                    case R.id.testResults:
                        getSupportFragmentManager().beginTransaction().replace(R.id.content, new ResultListFragment()).commit();
                        return true;
                    case R.id.settings:
                        getSupportFragmentManager().beginTransaction().replace(R.id.content, new PreferenceGlobalFragment()).commit();
                        return true;
                    default:
                        return false;
                }
            });
            /* TODO(aanorbel): Fix change in state(theme change from notification) changes the selected item.
                The proper fix would be to track the selected item as well as other properties in a `ViewModel`. */
            binding.bottomNavigation.setSelectedItemId(getIntent().getIntExtra(RES_ITEM, R.id.dashboard));
            /* Check if we are restoring the activity from a saved state first.
             * If we have a message to show, show it as a snackbar.
             * This is used to show the message from test completion.
             */
            if (savedInstanceState == null && getIntent().hasExtra(RES_SNACKBAR_MESSAGE)) {
                Snackbar.make(binding.getRoot(), getIntent().getStringExtra(RES_SNACKBAR_MESSAGE), Snackbar.LENGTH_SHORT)
                        .setAnchorView(binding.bottomNavigation)
                        .show();
            }
            if (notificationManager.shouldShowAutoTest()) {
                new ConfirmDialogFragment.Builder()
                        .withTitle(getString(R.string.Modal_Autorun_Modal_Title))
                        .withMessage(getString(R.string.Modal_Autorun_Modal_Text)
                                + "\n" + getString(R.string.Modal_Autorun_Modal_Text_Android))
                        .withPositiveButton(getString(R.string.Modal_SoundsGreat))
                        .withNegativeButton(getString(R.string.Modal_NotNow))
                        .withNeutralButton(getString(R.string.Modal_DontAskAgain))
                        .withExtra(AUTOTEST_DIALOG)
                        .build().show(getSupportFragmentManager(), null);
            } else if (notificationManager.shouldShow()) {
                new ConfirmDialogFragment.Builder()
                        .withTitle(getString(R.string.Modal_EnableNotifications_Title))
                        .withMessage(getString(R.string.Modal_EnableNotifications_Paragraph))
                        .withPositiveButton(getString(R.string.Modal_SoundsGreat))
                        .withNegativeButton(getString(R.string.Modal_NotNow))
                        .withNeutralButton(getString(R.string.Modal_DontAskAgain))
                        .withExtra(NOTIFICATION_DIALOG)
                        .build().show(getSupportFragmentManager(), null);
            }
            ThirdPartyServices.checkUpdates(this);
        }

        if (android.os.Build.VERSION.SDK_INT >= 29) {
            AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM);
        } else {
            if (preferenceManager.isDarkTheme()) {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
            } else {
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
            }
        }
        requestNotificationPermission();
        scheduleWorkers();
        onNewIntent(getIntent());
    }

    private void scheduleWorkers() {
        WorkManager.getInstance(this)
                .enqueueUniquePeriodicWork(
                AutoUpdateDescriptorsWorker.UPDATED_DESCRIPTORS_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                new PeriodicWorkRequest.Builder(AutoUpdateDescriptorsWorker.class, 24, TimeUnit.HOURS)
                        .setConstraints(
                                new Constraints.Builder()
                                        .setRequiredNetworkType(NetworkType.CONNECTED)
                                        .build()
                        ).build()
        );

        OneTimeWorkRequest manualWorkRequest = new OneTimeWorkRequest.Builder(ManualUpdateDescriptorsWorker.class)
                .setConstraints(
                        new Constraints.Builder()
                                .setRequiredNetworkType(NetworkType.CONNECTED)
                                .build()
                ).build();

        WorkManager.getInstance(this)
                .beginUniqueWork(
                        ManualUpdateDescriptorsWorker.UPDATED_DESCRIPTORS_WORK_NAME,
                        ExistingWorkPolicy.REPLACE,
                        manualWorkRequest
                ).enqueue();

        WorkManager.getInstance(this)
                .getWorkInfoByIdLiveData(manualWorkRequest.getId())
                .observe(this, workInfo -> {
                    if (workInfo.getState().isFinished()) {
                        if (workInfo.getState() == WorkInfo.State.SUCCEEDED) {
                            System.out.println(workInfo.getOutputData());
                        } else {
                            System.out.println("Failed");
                        }
                    }
                });
    }

    private void requestNotificationPermission() {

        requestPermissionLauncher = registerForActivityResult(
                new ActivityResultContracts.RequestPermission(),
                (result) -> {
                    if (!result) {
                        Snackbar.make(
                                binding.getRoot(),
                                "Please grant Notification permission from App Settings",
                                Snackbar.LENGTH_LONG
                        ).setAction(R.string.Settings_Title, view -> {
                            Intent intent = new Intent();
                            intent.setAction("android.settings.APP_NOTIFICATION_SETTINGS");
                            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);

                            //for Android 5-7
                            intent.putExtra("app_package", getPackageName());
                            intent.putExtra("app_uid", getApplicationInfo().uid);

                            // for Android 8 and above
                            intent.putExtra("android.provider.extra.APP_PACKAGE", getPackageName());

                            startActivity(intent);
                        }).show();
                    }
                }
        );
        NotificationUtility.setChannel(getApplicationContext(), CHANNEL_ID, getString(R.string.Settings_AutomatedTesting_Label), false, false, false);
        if (ContextCompat.checkSelfPermission(
                this,
                Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                requestPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS);
            }
        }
    }

    @Override
    protected void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        /**
         * Check if we are starting the activity from a link [Intent.ACTION_VIEW].
         * This is invoked when a v2 link is opened.
         * @see {@link org.openobservatory.ooniprobe.activity.OoniRunActivity#newIntent}. for v1 links.
         */
        if (Intent.ACTION_VIEW.equals(intent.getAction())) {
            Uri uri = intent.getData();
            // If the intent does not contain a link, do nothing.
            if (uri == null) {
                return;
            }

            Optional<Long> possibleRunId = getRunId(uri);

            // If the intent contains a link, but it is not a supported link or has a non-numerical `link_id`.
            if (!possibleRunId.isPresent()) {
                return;
            }

            // If the intent contains a link, but the `link_id` is zero.
            if (possibleRunId.get() == 0) {
                return;
            }

            TaskExecutor executor = new TaskExecutor();

            displayAddLinkProgressFragment(executor);

            executor.executeTask(() -> {
                try {
                    return descriptorManager.fetchDescriptorFromRunId(possibleRunId.get(), this);
                } catch (Exception exception) {
                    exception.printStackTrace();
                    ThirdPartyServices.logException(exception);
                    return null;
                }
            }, this::fetchDescriptorComplete);
        } else {
            /**
             * Check if we are starting the activity with an intent extra.
             * This is invoked when we are starting the activity from a notification or
             * when the activity is launched from the onboarding fragment
             * @see {@link org.openobservatory.ooniprobe.fragment.onboarding.Onboarding3Fragment#masterClick}.
             */
            if (intent.getExtras() != null) {
                if (intent.getExtras().containsKey(RES_ITEM)) {
                    binding.bottomNavigation.setSelectedItemId(intent.getIntExtra(RES_ITEM, R.id.dashboard));
                } else if (intent.getExtras().containsKey(NOTIFICATION_DIALOG)) {
                    new ConfirmDialogFragment.Builder()
                            .withTitle(intent.getExtras().getString("title"))
                            .withMessage(intent.getExtras().getString("message"))
                            .withNegativeButton("")
                            .withPositiveButton(getString(R.string.Modal_OK))
                            .build().show(getSupportFragmentManager(), null);
                }
            }
        }
    }

    /**
     * The task to fetch the descriptor from the link is completed.
     * <p>
     * This method is called when the `fetchDescriptorFromRunId` task is completed.
     * The `descriptorResponse` is the result of the task.
     * If the task is successful, the `descriptorResponse` is the descriptor.
     * Otherwise, the `descriptorResponse` is null.
     * <p>
     * If the `descriptorResponse` is not null, start the `AddDescriptorActivity`.
     * Otherwise, show an error message.
     *
     * @param descriptorResponse The result of the task.
     * @return null.
     */
    private Unit fetchDescriptorComplete(TestDescriptor descriptorResponse) {
        if (descriptorResponse != null) {
            startActivity(AddDescriptorActivity.newIntent(this, descriptorResponse));
        } else {
            // TODO(aanorbel): Provide a better error message.
            Snackbar.make(binding.getRoot(), R.string.Modal_Error, Snackbar.LENGTH_LONG)
                    .setAnchorView(binding.bottomNavigation) // NOTE:To avoid the `snackbar` from covering the bottom navigation.
                    .show();
        }
        removeProgressFragment();
        return Unit.INSTANCE;

    }

    /**
     * Display the progress fragment.
     * <p>
     * The progress fragment is used to display the progress of the task.
     * e.g. Fetching the descriptor from the link.
     *
     * @param executor The executor that will be used to execute the task.
     */
    private void displayAddLinkProgressFragment(TaskExecutor executor) {
        binding.dynamicProgressFragment.setVisibility(View.VISIBLE);
        getSupportFragmentManager().beginTransaction()
                .add(
                        R.id.dynamic_progress_fragment,
                        OONIRunDynamicProgressBar.newInstance(ProgressType.ADD_LINK, new OnActionListener() {
                            @Override
                            public void onActionButtonCLicked() {
                                executor.cancelTask();
                                removeProgressFragment();
                            }

                            @Override
                            public void onCloseButtonClicked() {
                                removeProgressFragment();
                            }
                        }),
                        OONIRunDynamicProgressBar.getTAG()
                ).commit();
    }

    /**
     * Extracts the run id from the provided Uri.
     * The run id can be in two different formats:
     * <p>
     * 1. ooni://runv2/link_id
     * 2. https://run.test.ooni.org/v2/link_id
     * <p>
     * The run id is the `link_id` in the link.
     * If the Uri contains a link, but the `link_id` is not a number, an empty Optional is returned.
     * If the Uri contains a link, but it is not a supported link, an empty Optional is returned.
     * <p>
     *
     * @param uri The Uri data.
     * @return An Optional containing the run id if the Uri contains a link with a valid `link_id`, or an empty Optional otherwise.
     */
    private Optional<Long> getRunId(Uri uri) {
        String host = uri.getHost();

        try {
            if ("runv2".equals(host)) {
                /**
                 * The run id is the first segment of the path.
                 * Launched when `Open Link in OONI Probe` is clicked.
                 * e.g. ooni://runv2/link_id
                 */
                return Optional.of(
                        Long.parseLong(
                                uri.getPathSegments().get(0)
                        )
                );
            } else if ("run.test.ooni.org".equals(host)) {
                /**
                 * The run id is the second segment of the path.
                 * Launched when the system recognizes this app can open this link
                 * and launches the app when a link is clicked.
                 * e.g. https://run.test.ooni.org/v2/link_id
                 */
                return Optional.of(
                        Long.parseLong(
                                uri.getPathSegments().get(1)
                        )
                );
            } else {
                // If the intent contains a link, but it is not a supported link.
                return Optional.absent();
            }
        } catch (Exception e) {
            // If the intent contains a link, but the `link_id` is not a number.
            e.printStackTrace();
            return Optional.absent();
        }
    }

    /**
     * Remove the progress fragment.
     * <p>
     * This method is called when the task is completed.
     */
    private void removeProgressFragment() {
        Fragment fragment = getSupportFragmentManager().findFragmentByTag(OONIRunDynamicProgressBar.getTAG());
        if (fragment != null && fragment.isAdded()) {
            getSupportFragmentManager().beginTransaction().remove(fragment).commit();
        }
        binding.dynamicProgressFragment.setVisibility(View.GONE);
    }

    @Override
    public void onConfirmation(Serializable extra, int i) {
        if (extra == null) return;
        if (extra.equals(NOTIFICATION_DIALOG)) {
            notificationManager.getUpdates(i == DialogInterface.BUTTON_POSITIVE);

            //If positive answer reload consents and init notification
            if (i == DialogInterface.BUTTON_POSITIVE) {
                ThirdPartyServices.reloadConsents((Application) getApplication());
            } else if (i == DialogInterface.BUTTON_NEUTRAL) {
                notificationManager.disableAskNotificationDialog();
            }
        }
        if (extra.equals(AUTOTEST_DIALOG)) {
            preferenceManager.setNotificationsFromDialog(i == DialogInterface.BUTTON_POSITIVE);
            if (i == DialogInterface.BUTTON_POSITIVE) {
                //For API < 23 we ignore battery optimization
                boolean isIgnoringBatteryOptimizations = true;
                if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                    PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
                    isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(getPackageName());
                }
                if (!isIgnoringBatteryOptimizations) {
                    Intent intent = new Intent();
                    intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                    intent.setData(Uri.parse("package:" + getPackageName()));
                    startActivityForResult(intent, PreferenceManager.IGNORE_OPTIMIZATION_REQUEST);
                } else {
                    preferenceManager.enableAutomatedTesting();
                    ServiceUtil.scheduleJob(this);
                }
            } else if (i == DialogInterface.BUTTON_NEUTRAL) {
                preferenceManager.disableAskAutomaticTestDialog();
            }
        }
        if (extra.equals(BATTERY_DIALOG)) {
            preferenceManager.setNotificationsFromDialog(i == DialogInterface.BUTTON_POSITIVE);
            if (i == DialogInterface.BUTTON_POSITIVE) {
                Intent intent = new Intent();
                intent.setAction(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
                intent.setData(Uri.parse("package:" + getPackageName()));
                startActivityForResult(intent, PreferenceManager.IGNORE_OPTIMIZATION_REQUEST);
            }
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PreferenceManager.IGNORE_OPTIMIZATION_REQUEST) {
            PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
            //For API < 23 we ignore battery optimization
            boolean isIgnoringBatteryOptimizations = true;
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                isIgnoringBatteryOptimizations = pm.isIgnoringBatteryOptimizations(getPackageName());
            }
            if (isIgnoringBatteryOptimizations) {
                preferenceManager.enableAutomatedTesting();
                ServiceUtil.scheduleJob(this);
            } else {
                new ConfirmDialogFragment.Builder()
                        .withMessage(getString(R.string.Modal_Autorun_BatteryOptimization))
                        .withPositiveButton(getString(R.string.Modal_OK))
                        .withNegativeButton(getString(R.string.Modal_Cancel))
                        .withExtra(BATTERY_DIALOG)
                        .build().show(getSupportFragmentManager(), null);
            }
        } else if (requestCode == PreferenceManager.ASK_UPDATE_APP) {
            if (resultCode != RESULT_OK) {
                //We don't need to check the result for now
            }
        }
    }
}
