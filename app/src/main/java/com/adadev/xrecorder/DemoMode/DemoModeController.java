package com.adadev.xrecorder.DemoMode;

import android.content.Context;
import android.content.Intent;

import com.topjohnwu.superuser.Shell;

public class DemoModeController {
    private static final String DEMO_MODE_ON = "sysui_tuner_demo_on";

    private static final String[] STATUS_ICONS = {
            "volume",
            "bluetooth",
            "location",
            "alarm",
            "zen",
            "sync",
            "tty",
            "eri",
            "mute",
            "speakerphone",
            "managed_profile",
    };

    public static void allowDemoMode(Context context) {
        Shell.su("settings put global sysui_demo_allowed 1",
                "settings put global " + DEMO_MODE_ON + " 1").submit(result -> {
            if (result.isSuccess())
                EnableDemoMode(context);
        });
    }

    public static void EnableDemoMode(Context context) {
        Intent intent = new Intent(DemoMode.ACTION_DEMO);

        intent.putExtra(DemoMode.EXTRA_COMMAND, DemoMode.COMMAND_ENTER);
        context.sendBroadcast(intent);

        intent.putExtra(DemoMode.EXTRA_COMMAND, DemoMode.COMMAND_CLOCK);

        String demoTime = "1010"; // 10:10, a classic choice of horologists
        try {
            String[] versionParts = android.os.Build.VERSION.RELEASE.split("\\.");
            int majorVersion = Integer.valueOf(versionParts[0]);
            demoTime = String.format("%02d00", majorVersion % 24);
        } catch (IllegalArgumentException ex) {
        }
        intent.putExtra("hhmm", demoTime);
        context.sendBroadcast(intent);

        intent.putExtra(DemoMode.EXTRA_COMMAND, DemoMode.COMMAND_NETWORK);
        intent.putExtra("wifi", "show");
        intent.putExtra("mobile", "show");
        intent.putExtra("sims", "1");
        intent.putExtra("nosim", "false");
        intent.putExtra("level", "4");
        intent.putExtra("datatype", "lte");
        context.sendBroadcast(intent);

        intent.putExtra("fully", "true");
        context.sendBroadcast(intent);

        intent.putExtra(DemoMode.EXTRA_COMMAND, DemoMode.COMMAND_BATTERY);
        intent.putExtra("level", "100");
        intent.putExtra("plugged", "false");
        context.sendBroadcast(intent);

        intent.putExtra(DemoMode.EXTRA_COMMAND, DemoMode.COMMAND_STATUS);
        for (String icon : STATUS_ICONS) {
            intent.putExtra(icon, "hide");
        }
        context.sendBroadcast(intent);

        intent.putExtra(DemoMode.EXTRA_COMMAND, DemoMode.COMMAND_NOTIFICATIONS);
        intent.putExtra("visible", "false");
        context.sendBroadcast(intent);
    }

    public static void stopDemoMode(Context context) {
        Intent intent = new Intent(DemoMode.ACTION_DEMO);
        intent.putExtra(DemoMode.EXTRA_COMMAND, DemoMode.COMMAND_EXIT);
        context.sendBroadcast(intent);
    }
}
