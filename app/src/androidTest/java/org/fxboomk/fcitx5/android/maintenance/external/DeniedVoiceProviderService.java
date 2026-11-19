/* SPDX-License-Identifier: LGPL-2.1-or-later */
package org.fxboomk.fcitx5.android.maintenance.external;

import android.app.Service;
import android.content.Intent;
import android.os.IBinder;

/** Host does not request this service permission; its Binder must never be called. */
public class DeniedVoiceProviderService extends Service {
    @Override public IBinder onBind(Intent intent) {
        throw new AssertionError("Expected bind permission denial before entering the provider");
    }
}
