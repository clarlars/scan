package org.opendatakit.scan.fragments;

import android.content.SharedPreferences;
import android.util.Log;
import org.opendatakit.common.android.activities.IAppAwareActivity;
import org.opendatakit.common.android.activities.IInitResumeActivity;
import org.opendatakit.common.android.fragment.AlertDialogFragment;
import org.opendatakit.common.android.fragment.AlertDialogFragment.ConfirmAlertDialog;
import org.opendatakit.common.android.fragment.ProgressDialogFragment;
import org.opendatakit.common.android.fragment.ProgressDialogFragment.CancelProgressDialog;
import org.opendatakit.common.android.listener.DatabaseConnectionListener;
import org.opendatakit.common.android.listener.InitializationListener;
import org.opendatakit.common.android.logging.WebLogger;

import android.app.Fragment;
import android.app.FragmentManager;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import org.opendatakit.scan.R;
import org.opendatakit.scan.application.Scan;
import org.opendatakit.scan.utils.ScanUtils;

import java.io.File;
import java.io.IOException;
import java.io.PrintWriter;
import java.util.ArrayList;

/**
 * Attempt to initialize data directories using the APK Expansion files.
 *
 * @author jbeorse@cs.washington.edu
 */
public class InitializationFragment extends Fragment
    implements InitializationListener, ConfirmAlertDialog, CancelProgressDialog,
    DatabaseConnectionListener {

  private static final String t = "InitializationFragment";

  private static final int ID = R.layout.copy_expansion_files_layout;

  private static enum DialogState {
    Init, Progress, Alert, None
  }

  ;

  // keys for the data being retained

  private static final String DIALOG_TITLE = "dialogTitle";
  private static final String DIALOG_MSG = "dialogMsg";
  private static final String DIALOG_STATE = "dialogState";

  // data to retain across orientation changes

  private String mAlertTitle;
  private String mAlertMsg;
  private DialogState mDialogState = DialogState.Init;
  private DialogState mPendingDialogState = DialogState.Init;

  // data that is not retained

  private View view;

  @Override
  public void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
  }

  @Override
  public void onActivityCreated(Bundle savedInstanceState) {
    super.onActivityCreated(savedInstanceState);
  }

  @Override
  public View onCreateView(LayoutInflater inflater, ViewGroup container,
      Bundle savedInstanceState) {
    view = inflater.inflate(ID, container, false);

    if (savedInstanceState != null) {

      // to restore alert dialog.
      if (savedInstanceState.containsKey(DIALOG_TITLE)) {
        mAlertTitle = savedInstanceState.getString(DIALOG_TITLE);
      }
      if (savedInstanceState.containsKey(DIALOG_MSG)) {
        mAlertMsg = savedInstanceState.getString(DIALOG_MSG);
      }
      if (savedInstanceState.containsKey(DIALOG_STATE)) {
        mDialogState = DialogState.valueOf(savedInstanceState.getString(DIALOG_STATE));
      }
    }

    return view;
  }

  /**
   * Starts the download task and shows the progress dialog.
   */
  private void intializeAppName() {
    // set up the first dialog, but don't show it...
    mAlertTitle = getString(R.string.configuring_app,
        getString(Scan.getInstance().getApkDisplayNameResourceId()));
    mAlertMsg = getString(R.string.please_wait);
    mDialogState = DialogState.Progress;

    restoreProgressDialog();

    // launch the copy operation
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
        .i(t, "initializeAppName called ");
    Scan.getInstance().initializeAppName(((IAppAwareActivity) getActivity()).getAppName(), this);


  }

  @Override
  public void onSaveInstanceState(Bundle outState) {
    super.onSaveInstanceState(outState);
    if (mAlertTitle != null) {
      outState.putString(DIALOG_TITLE, mAlertTitle);
    }
    if (mAlertMsg != null) {
      outState.putString(DIALOG_MSG, mAlertMsg);
    }
    outState.putString(DIALOG_STATE, mDialogState.name());
  }

  @Override
  public void onResume() {
    super.onResume();

    if (mDialogState == DialogState.Init) {
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
          .i(t, "onResume -- calling initializeAppName");
      intializeAppName();
    } else {

      if (mDialogState == DialogState.Progress) {
        restoreProgressDialog();
      } else if (mDialogState == DialogState.Alert) {
        restoreAlertDialog();
      }

      // re-attach to the task for task notifications...
      Scan.getInstance().establishInitializationListener(this);
    }
  }

  @Override
  public void onStart() {
    super.onStart();
    Scan.getInstance().possiblyFireDatabaseCallback(getActivity(), this);
  }

  @Override
  public void onPause() {
    FragmentManager mgr = getFragmentManager();

    // dismiss dialogs...
    AlertDialogFragment alertDialog = (AlertDialogFragment) mgr.findFragmentByTag("alertDialog");
    if (alertDialog != null) {
      alertDialog.dismiss();
    }
    ProgressDialogFragment progressDialog = (ProgressDialogFragment) mgr
        .findFragmentByTag("progressDialog");
    if (progressDialog != null) {
      progressDialog.dismiss();
    }
    mPendingDialogState = DialogState.None;
    super.onPause();
  }

  @Override
  public void initializationComplete(boolean overallSuccess, ArrayList<String> result) {

    /* Add runtime initialization */

    // Create output dir if it doesn't exist
    new File(ScanUtils.getOutputDirPath()).mkdirs();

    try {

      //Creates a .nomedia file to prevent the images from showing up in the gallery.
      /*
      new File(ScanUtils.getSystemPath() + File.separator + ".nomedia").createNewFile();
      new File(ScanUtils.getConfigPath() + File.separator + ".nomedia").createNewFile();
      new File(ScanUtils.getOutputDirPath() + File.separator + ".nomedia").createNewFile();
      */

      // TODO: Only adding dummy data while sync doesn't support empty files. Remove dummy data
      // when that is fixed
      PrintWriter systemPW = new PrintWriter(ScanUtils.getSystemPath() + File.separator + ""
          + ".nomedia");
      PrintWriter configPW = new PrintWriter(ScanUtils.getConfigPath() + File.separator + ""
          + ".nomedia");
      PrintWriter outputPW = new PrintWriter(ScanUtils.getOutputDirPath() + File.separator + ""
          + ".nomedia");

      systemPW.print("Dummy data");
      configPW.print("Dummy data");
      outputPW.print("Dummy data");

      systemPW.close();
      configPW.close();
      outputPW.close();
    } catch (IOException e) {
      e.printStackTrace();
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
          .i(t, "Error creating nomedia");
    }

    try {
      dismissProgressDialog();
    } catch (IllegalArgumentException e) {
      WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
          .i(t, "Attempting to close a dialog that was not previously opened");
    }

    /* Finish initialization */

    Scan.getInstance().clearInitializationTask();

    if (overallSuccess && result.isEmpty()) {
      // do not require an OK if everything went well
      Fragment progress = getFragmentManager().findFragmentByTag("progressDialog");
      if (progress != null) {
        ((ProgressDialogFragment) progress).dismiss();
        mDialogState = DialogState.None;
      }

      ((IInitResumeActivity) getActivity()).initializationCompleted();
      return;
    }

    StringBuilder b = new StringBuilder();
    for (String k : result) {
      b.append(k);
      b.append("\n\n");
    }

    createAlertDialog(overallSuccess ?
        getString(R.string.initialization_complete) :
        getString(R.string.initialization_failed), b.toString().trim());
  }

  private void restoreProgressDialog() {
    Fragment alert = getFragmentManager().findFragmentByTag("alertDialog");
    if (alert != null) {
      ((AlertDialogFragment) alert).dismiss();
    }

    Fragment dialog = getFragmentManager().findFragmentByTag("progressDialog");

    if (dialog != null && ((ProgressDialogFragment) dialog).getDialog() != null) {
      mDialogState = DialogState.Progress;
      ((ProgressDialogFragment) dialog).getDialog().setTitle(mAlertTitle);
      ((ProgressDialogFragment) dialog).setMessage(mAlertMsg);

    } else {

      ProgressDialogFragment f = ProgressDialogFragment
          .newInstance(mAlertTitle, mAlertMsg);

      mDialogState = DialogState.Progress;
      if (mPendingDialogState != mDialogState) {
        mPendingDialogState = mDialogState;
        f.show(getFragmentManager(), "progressDialog");
      }
    }
  }

  private void updateProgressDialogMessage(String message) {
    if (mDialogState == DialogState.Progress) {
      mAlertTitle = getString(R.string.configuring_app,
          getString(Scan.getInstance().getApkDisplayNameResourceId()));
      mAlertMsg = message;
      restoreProgressDialog();
    }
  }

  private void dismissProgressDialog() {
    if (mDialogState == DialogState.Progress) {
      mDialogState = DialogState.None;
    }
    Fragment dialog = getFragmentManager().findFragmentByTag("progressDialog");
    if (dialog != null) {
      ((ProgressDialogFragment) dialog).dismiss();
      mPendingDialogState = DialogState.None;
    }
  }

  private void restoreAlertDialog() {
    Fragment progress = getFragmentManager().findFragmentByTag("progressDialog");
    if (progress != null) {
      ((ProgressDialogFragment) progress).dismiss();
    }

    Fragment dialog = getFragmentManager().findFragmentByTag("alertDialog");

    if (dialog != null && ((AlertDialogFragment) dialog).getDialog() != null) {
      mDialogState = DialogState.Alert;
      ((AlertDialogFragment) dialog).getDialog().setTitle(mAlertTitle);
      ((AlertDialogFragment) dialog).setMessage(mAlertMsg);

    } else {

      AlertDialogFragment f = AlertDialogFragment.newInstance(getId(), mAlertTitle, mAlertMsg);

      mDialogState = DialogState.Alert;
      if (mPendingDialogState != mDialogState) {
        mPendingDialogState = mDialogState;
        f.show(getFragmentManager(), "alertDialog");
      }
    }
  }

  @Override
  public void okAlertDialog() {
    mDialogState = DialogState.None;
    ((IInitResumeActivity) getActivity()).initializationCompleted();
  }

  private void createAlertDialog(String title, String message) {
    mAlertMsg = message;
    mAlertTitle = title;
    restoreAlertDialog();
  }

  @Override
  public void initializationProgressUpdate(String displayString) {
    updateProgressDialogMessage(displayString);
  }

  @Override
  public void cancelProgressDialog() {
    WebLogger.getLogger(((IAppAwareActivity) getActivity()).getAppName())
        .i(t, "cancelProgressDialog -- calling cancelInitializationTask");
    // signal the task that we want it to be cancelled.
    // but keep the notification path...
    // the task will call back with a copyExpansionFilesComplete()
    // to report status (cancelled).
    Scan.getInstance().cancelInitializationTask();
  }

  @Override
  public void databaseAvailable() {
    if (mDialogState == DialogState.Progress) {
      Scan.getInstance().initializeAppName(((IAppAwareActivity) getActivity()).getAppName(), this);
    }
  }

  @Override
  public void databaseUnavailable() {
    if (mDialogState == DialogState.Progress) {
      updateProgressDialogMessage(getString(R.string.database_unavailable));
    }
  }

}
