package spqr.n95decon;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.DialogFragment;
import android.support.v7.app.AlertDialog;

public class NewRoundDialogFragment extends DialogFragment {

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {

        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());

        builder.setPositiveButton("Discard", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                NodeManager.startNewRound();

                MainActivity activity = (MainActivity) getActivity();
                assert activity != null;
                Intent intentBLE = new Intent(activity, BLEService.class);
                activity.startService(intentBLE);

                activity.toastNotice("New Round Started");
                activity.updateUI();
            }
        });

        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            public void onClick(DialogInterface dialog, int id) {
                // Do nothing
            }
        });

        builder.setMessage("You haven't exported data. Do you want to discard data and start a new round anyway?");
        builder.setTitle("Starting New Round");
        return builder.create();
    }
}
