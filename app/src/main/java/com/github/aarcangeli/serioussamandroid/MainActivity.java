package com.github.aarcangeli.serioussamandroid;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlarmManager;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.app.ProgressDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.AssetManager;
import android.content.pm.ActivityInfo;
import android.content.pm.PackageManager;
import android.graphics.Point;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.hardware.input.InputManager;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.preference.PreferenceManager;
import androidx.annotation.NonNull;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import android.util.Log;
import android.view.Display;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.InputDevice;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.InputMethodManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Toast;
import com.github.aarcangeli.serioussamandroid.input.InputProcessor;
import com.github.aarcangeli.serioussamandroid.views.JoystickView;
import com.hold1.keyboardheightprovider.KeyboardHeightProvider;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.greenrobot.eventbus.ThreadMode;

import java.io.File;
import java.io.OutputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.util.Locale;
import java.net.NetworkInterface;
import java.net.InetAddress;
import java.net.Inet4Address;
import java.util.Enumeration;

import static com.github.aarcangeli.serioussamandroid.NativeEvents.EditTextEvent;
import static com.github.aarcangeli.serioussamandroid.NativeEvents.ErrorEvent;
import static com.github.aarcangeli.serioussamandroid.NativeEvents.GameState;
import static com.github.aarcangeli.serioussamandroid.NativeEvents.OpenSettingsEvent;
import static com.github.aarcangeli.serioussamandroid.NativeEvents.UpdateUIEvent;
import static com.github.aarcangeli.serioussamandroid.NativeEvents.RestartEvent;
import static com.github.aarcangeli.serioussamandroid.NativeEvents.StateChangeEvent;
import static com.github.aarcangeli.serioussamandroid.input.VirtualKeyboard.*;
import static com.github.aarcangeli.serioussamandroid.views.JoystickView.Listener;

public class MainActivity extends Activity {
    public static final String TAG = "SeriousSamJava";
    private final int REQUEST_WRITE_STORAGE = 1;
    private static final int AXIS_MOVE_UD = 0;
    private static final int AXIS_MOVE_LR = 1;
    private static final int AXIS_MOVE_FB = 2;
    private static final int AXIS_TURN_UD = 3;
    private static final int AXIS_TURN_LR = 4;
    private static final int AXIS_TURN_BK = 5;
    private static final int AXIS_LOOK_UD = 6;
    private static final int AXIS_LOOK_LR = 7;
    private static final int AXIS_LOOK_BK = 8;

    private static final float MULT_VIEW_CONTROLLER = 2.5f;
    private static final float MULT_VIEW_TRACKER = 0.4f;
    private static final float MULT_VIEW_GYROSCOPE = 0.8f;

    private SeriousSamSurface glSurfaceView;
    private File homeDir;
    private boolean isGameStarted = false;
    private SensorManager sensorManager;
    private SensorEventListener motionListener;
    private volatile GameState gameState = GameState.LOADING;
    private volatile int bombs;
    private boolean useGyroscope;
    private String showTouchController;
    private float gyroSensibility;
    private float aimViewSensibility;
    private float ctrlAimSensibility;
    public float deadZone;
    private boolean enableTouchController;
    private String din_uiScale;
    private String ui_drawBanner;
    public float uiScale;
    public boolean ButtonsMapping = false;
    public boolean isTracking;
	public float input_overlayX, input_overlayY;
	public View currentView;
	public float lastx, lasty;
    private InputProcessor processor = new InputProcessor();
    private InputMethodManager inputMethodManager;

    private KeyboardHeightProvider keyboardHeightProvider;
    private KeyboardHeightProvider.KeyboardListener listener = new KeyboardHeightProvider.KeyboardListener() {
        @Override
        public void onHeightChanged(int height) {
            Display display = getWindowManager().getDefaultDisplay();
            Point size = new Point();
            display.getSize(size);
            executeShell(String.format(Locale.ENGLISH, "con_fHeightFactor = %.6f", (size.y - height) / (float) size.y));
        }
    };

    private void copyFolder(String name) throws IOException {
        AssetManager assetManager = getAssets();
        String[] files = assetManager.list(name);
        if (files == null) {
            return;
        }

        File outputFilder = new File(homeDir, name);
        outputFilder.mkdirs();

        for (String filename : files) {
            try (InputStream in = assetManager.open(name + "/" + filename);
                 OutputStream out = new FileOutputStream(new File(outputFilder, filename))) {
                byte[] buffer = new byte[1024];
                int read;
                while ((read = in.read(buffer)) != -1) {
                    out.write(buffer, 0, read);
                }
            }
        }
    }

    @Override
    @SuppressLint("ClickableViewAccessibility")
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        inputMethodManager = (InputMethodManager) getSystemService(Context.INPUT_METHOD_SERVICE);

        setContentView(R.layout.main_screen);
        glSurfaceView = findViewById(R.id.main_content);
        glSurfaceView.setActivity(this);

        Button loadBtn = findViewById(R.id.buttonLoad);
        loadBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                executeShell("sam_bMenuLoad=1;");
                return true;
            }
        });

        Button saveBtn = findViewById(R.id.buttonSave);
        saveBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
                executeShell("sam_bMenuSave=1;");
                return true;
            }
        });

        Button settingsBtn = findViewById(R.id.settingsBtn);
        settingsBtn.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View v) {
			if (ButtonsMapping == false){
				Toast toast = Toast.makeText(MainActivity.this, "Buttons mapping: ON",Toast.LENGTH_SHORT);
				toast.show();
				ButtonsMapping = true;
				findViewById(R.id.buttonApply).setVisibility(View.VISIBLE);
				findViewById(R.id.input_SeriousBomb).setVisibility(View.VISIBLE);
				findViewById(R.id.buttonPlus).setVisibility(View.VISIBLE);
				findViewById(R.id.buttonMinus).setVisibility(View.VISIBLE);

				isTracking = false;
			}
                return true;
            }
        });
		
        findViewById(R.id.input_use).setOnTouchListener(new MyBtnListener(KeyEvent.KEYCODE_BUTTON_R2));
        findViewById(R.id.input_crunch).setOnTouchListener(new MyBtnListener(KeyEvent.KEYCODE_BUTTON_B));
        findViewById(R.id.input_jump).setOnTouchListener(new MyBtnListener(KeyEvent.KEYCODE_BUTTON_A));
        findViewById(R.id.buttonPrev).setOnTouchListener(new MyBtnListener(KeyEvent.KEYCODE_DPAD_LEFT));
        findViewById(R.id.buttonNext).setOnTouchListener(new MyBtnListener(KeyEvent.KEYCODE_DPAD_RIGHT));
        findViewById(R.id.input_fire).setOnTouchListener(new MyBtnListener(KeyEvent.KEYCODE_BUTTON_R1));
        findViewById(R.id.input_SeriousBomb).setOnTouchListener(new MyBtnListener(KeyEvent.KEYCODE_BUTTON_Y));
        findViewById(R.id.bgTrackerView).setOnTouchListener(new MyBtnListener());
		
        JoystickView joystick = findViewById(R.id.input_overlay);
        joystick.setListener(new Listener() {
            @Override
            public void onMove(float deltaX, float deltaY, MotionEvent ev) {
            if (gameState == GameState.NORMAL) {
                float dX, dY;
        JoystickView joystick = findViewById(R.id.input_overlay);
        	if (ButtonsMapping) {
					SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
					SharedPreferences.Editor sharedPreferencesEditor = preferences.edit();
					joystick.ButtonsMapping = true;
					findViewById(R.id.input_overlay).setX(ev.getRawX() + -Utils.convertPixelsToDp(joystick.padPosX, MainActivity.this));
					findViewById(R.id.input_overlay).setY(ev.getRawY() + -Utils.convertPixelsToDp(joystick.padPosY, MainActivity.this));
					sharedPreferencesEditor.putFloat("input_overlayX", ev.getRawX() + -Utils.convertPixelsToDp(joystick.padPosX, MainActivity.this)).apply();
					sharedPreferencesEditor.putFloat("input_overlayY", ev.getRawY() + -Utils.convertPixelsToDp(joystick.padPosY, MainActivity.this)).apply();
                } else {
					joystick.ButtonsMapping = false;
					setAxisValue(AXIS_MOVE_LR, deltaX);
					setAxisValue(AXIS_MOVE_FB, deltaY);
                }
                }
            }
        });

        InputManager systemService = (InputManager) getSystemService(Context.INPUT_SERVICE);
        systemService.registerInputDeviceListener(new InputManager.InputDeviceListener() {
            @Override
            public void onInputDeviceAdded(int deviceId) {
                updateSoftKeyboardVisible();
            }

            @Override
            public void onInputDeviceRemoved(int deviceId) {
                updateSoftKeyboardVisible();
            }

            @Override
            public void onInputDeviceChanged(int deviceId) {
                updateSoftKeyboardVisible();
            }
        }, null);

        homeDir = getHomeDir();
        Log.i(TAG, "HomeDir: " + homeDir);
        Log.i(TAG, "LibDir: " + getLibDir(this));

        sensorManager = (SensorManager) getSystemService(Context.SENSOR_SERVICE);

        motionListener = new SensorEventListener() {
            @Override
            public void onSensorChanged(SensorEvent event) {
                if (event.sensor.getType() == Sensor.TYPE_GYROSCOPE && gameState == GameState.NORMAL && useGyroscope && enableTouchController) {
                    float axisX = event.values[0];
                    float axisY = event.values[1];
                    float axisZ = event.values[2];
                    shiftAxisValue(AXIS_LOOK_LR, axisX * MULT_VIEW_GYROSCOPE * gyroSensibility);
                    shiftAxisValue(AXIS_LOOK_UD, -axisY * MULT_VIEW_GYROSCOPE * gyroSensibility);
                }
            }

            @Override
            public void onAccuracyChanged(Sensor sensor, int accuracy) {
            }
        };

        if (!hasStoragePermission(this)) {
            ActivityCompat.requestPermissions(this, new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE}, REQUEST_WRITE_STORAGE);
        } else {
            startGame();
        }

        updateSoftKeyboardVisible();

        keyboardHeightProvider = new KeyboardHeightProvider(this);

//        getWindow().getDecorView().getViewTreeObserver().addOnPreDrawListener(new ViewTreeObserver.OnPreDrawListener() {
//            @Override
//            public boolean onPreDraw() {
//                return true;
//            }
//        });

//        getWindow().getDecorView().getViewTreeObserver().addOnGlobalLayoutListener(new ViewTreeObserver.OnGlobalLayoutListener() {
//            @Override
//            public void onGlobalLayout() {
//
//            }
//        });
    }

    public void DinamicUI() {
        if ("On".equalsIgnoreCase(din_uiScale)) {
            uiScale = Utils.convertDpToPixel(1.0f, this) * glSurfaceView.getScale();
            Log.i(TAG, "Dinamic UI Enabled");
        } else if ("Off".equalsIgnoreCase(din_uiScale)) {
            uiScale = 1.0f;
            Log.i(TAG, "Dinamic UI Disabled");
        } else {
            uiScale = Utils.convertDpToPixel(1.0f, this) * glSurfaceView.getScale();
            Log.i(TAG, "Dinamic UI Enabled");
        }
    }

    public void drawBanner() {
        if ("On".equalsIgnoreCase(ui_drawBanner)) {
            executeShell("ui_drawBanner=1;");
            Log.i(TAG, "DrawBanner Enabled");
        } else if ("Off".equalsIgnoreCase(ui_drawBanner)) {
            executeShell("ui_drawBanner=0;");
            Log.i(TAG, "DrawBanner Disabled");
        } else {
            executeShell("ui_drawBanner=1;");
            Log.i(TAG, "DrawBanner Enabled");
        }
    }

    public String getWifiIP() {
			Enumeration<InetAddress> en;
		try {
			NetworkInterface wlan0 = NetworkInterface.getByName("wlan0");
			NetworkInterface ap0 = NetworkInterface.getByName("ap0");
			if (wlan0.isUp()) {
			en = wlan0.getInetAddresses();
			} else {
			en = ap0.getInetAddresses();
			}
			while (en.hasMoreElements()) {
				InetAddress inetAddress = en.nextElement();
				if (!inetAddress.isLoopbackAddress() && inetAddress instanceof Inet4Address) {
					return inetAddress.getHostAddress();
				}
			}
		} catch (Exception e) {}
		return "Wifi not working";
	}

    public void updateSoftKeyboardVisible() {
        enableTouchController = false;
        if (gameState == GameState.NORMAL) {
            if ("Yes".equalsIgnoreCase(showTouchController)) {
                enableTouchController = true;
            } else if ("No".equalsIgnoreCase(showTouchController)) {
                enableTouchController = false;
            } else {
                enableTouchController = !Utils.isThereControllers();
            }
        }
        int keyboardVisibility = enableTouchController ? View.VISIBLE : View.GONE;
        findViewById(R.id.input_overlay).setVisibility(keyboardVisibility);
        findViewById(R.id.input_crunch).setVisibility(keyboardVisibility);
        findViewById(R.id.input_jump).setVisibility(keyboardVisibility);
        findViewById(R.id.input_fire).setVisibility(keyboardVisibility);
        findViewById(R.id.input_use).setVisibility(keyboardVisibility);
        findViewById(R.id.buttonPrev).setVisibility(keyboardVisibility);
        findViewById(R.id.buttonNext).setVisibility(keyboardVisibility);
        findViewById(R.id.bgTrackerView).setVisibility(keyboardVisibility);
        findViewById(R.id.settingsBtn).setVisibility((gameState == GameState.NORMAL || gameState == GameState.DEMO) ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonLoad).setVisibility(gameState == GameState.NORMAL ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonConsole).setVisibility(gameState == GameState.NORMAL ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonSave).setVisibility(gameState == GameState.NORMAL ? View.VISIBLE : View.GONE);
        findViewById(R.id.input_SeriousBomb).setVisibility(enableTouchController && bombs > 0 ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonApply).setVisibility(enableTouchController && ButtonsMapping == true ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonPlus).setVisibility(enableTouchController && ButtonsMapping == true ? View.VISIBLE : View.GONE);
        findViewById(R.id.buttonMinus).setVisibility(enableTouchController && ButtonsMapping == true ? View.VISIBLE : View.GONE);

        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        if (gameState == GameState.NORMAL) {
		findViewById(R.id.main_content).requestPointerCapture();
		findViewById(R.id.main_content).setOnCapturedPointerListener(new View.OnCapturedPointerListener() {
		  @Override
		  public boolean onCapturedPointer (View view, MotionEvent event) {
				int action = event.getActionMasked();
				int mouseButton = 1;
				try {
					Object object = event.getClass().getMethod("getButtonState").invoke(event);
					if (object != null) {
						mouseButton = (Integer) object;
					}
				} catch(Exception ignored) {
				}
			   			   
				//nDispatchKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, event.getAxisValue(MotionEvent.AXIS_VSCROLL) < -.0f ? 1 : 0);
				//nDispatchKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, event.getAxisValue(MotionEvent.AXIS_VSCROLL) > .0f ? 1 : 0);
				float scroll = event.getAxisValue(MotionEvent.AXIS_VSCROLL);
				
				float x = event.getX();
				float y = event.getY();	
				
				shiftAxisValue(AXIS_LOOK_LR, -x * MULT_VIEW_TRACKER * aimViewSensibility);
				shiftAxisValue(AXIS_LOOK_UD, -y * MULT_VIEW_TRACKER * aimViewSensibility);
				
				nSendMouseNative(mouseButton, action, scroll);
				lastx = x;
				lasty = y;
			return true;
		  }
		});
        } else {
			findViewById(R.id.main_content).releasePointerCapture();
		}

        if (gameState == GameState.MENU || gameState == GameState.CONSOLE) {
            getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_FULLSCREEN);
        } else {
            getWindow().getDecorView().setSystemUiVisibility(
                    View.SYSTEM_UI_FLAG_LAYOUT_STABLE |
                            View.SYSTEM_UI_FLAG_LAYOUT_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_HIDE_NAVIGATION |
                            View.SYSTEM_UI_FLAG_FULLSCREEN |
                            View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        }

        if (gameState == GameState.CONSOLE) {
            if (glSurfaceView.requestFocus()) {
                inputMethodManager.showSoftInput(glSurfaceView, 0);
            }
        }
    }

    @Override
    protected void onStart() {
        super.onStart();
        sensorManager.registerListener(motionListener, sensorManager.getDefaultSensor(Sensor.TYPE_GYROSCOPE), 10000);
        EventBus.getDefault().register(this);
        keyboardHeightProvider.addKeyboardListener(listener);
    }

    @Override
    protected void onStop() {
        super.onStop();
        sensorManager.unregisterListener(motionListener);
        EventBus.getDefault().unregister(this);
        keyboardHeightProvider.removeKeyboardListener(listener);
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onFatalError(final ErrorEvent event) {
        AlertDialog.Builder dlgAlert = new AlertDialog.Builder(this);
        dlgAlert.setMessage(event.message);
        dlgAlert.setTitle("Fatal Error");
        dlgAlert.setPositiveButton("Close", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                if (event.fatal) {
                    System.exit(1);
                }
            }
        });
        dlgAlert.setCancelable(false);
        dlgAlert.create().show();
    }

    @Subscribe(sticky = true, threadMode = ThreadMode.MAIN)
    public void onConsoleVisibilityChange(StateChangeEvent event) {
        gameState = event.state;
        bombs = event.bombs;
        updateSoftKeyboardVisible();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void openSettings(OpenSettingsEvent event) {
        startActivity(new Intent(this, SettingsActivity.class));
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void editText(final EditTextEvent event) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        // Set up the input
        final EditText input = new EditText(this);
        input.setText(event.defaultText);
        builder.setView(input);

        // Set up the buttons
        builder.setPositiveButton("OK", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                nConfirmEditText(input.getText().toString());
            }
        });
        builder.setNegativeButton("Cancel", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.cancel();
                nCancelEditText();
            }
        });

        builder.show();
    }

    @Override
    protected void onResume() {
        super.onResume();
        // hide verything from the screen
        setRequestedOrientation(ActivityInfo.SCREEN_ORIENTATION_USER_LANDSCAPE);

        if (isGameStarted) {
            glSurfaceView.start();
        }

        glSurfaceView.syncOptions();
        syncOptions();
        keyboardHeightProvider.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        glSurfaceView.stop();
        executeShell("HideConsole();");
        executeShell("HideComputer();");
        executeShell("SaveOptions();");
        keyboardHeightProvider.onPause();
    }

    @Subscribe(threadMode = ThreadMode.MAIN)
    public void restartEvent(RestartEvent event) {
        final MainActivity context = MainActivity.this;
        runOnUiThread(new Runnable() {
            public void run() {
                ProgressDialog dialog = new ProgressDialog(context);
                dialog.setProgressStyle(ProgressDialog.STYLE_SPINNER);
                dialog.setTitle("SeriousSam");
                dialog.setMessage("Restarting");
                dialog.setCancelable(false);
                dialog.setMax(0);
                dialog.show();
            }
        });
        new Handler().postDelayed(new Runnable() {
            public void run() {
                // restart
                PendingIntent mPendingIntent = PendingIntent.getActivity(MainActivity.this, 1000, getIntent(), PendingIntent.FLAG_CANCEL_CURRENT);
                AlarmManager mgr = (AlarmManager) getSystemService(Context.ALARM_SERVICE);
                mgr.set(AlarmManager.RTC, System.currentTimeMillis() + 100, mPendingIntent);
                System.exit(0);
            }
        }, 1000);
    }

    private static boolean hasStoragePermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED;
    }

    @Override
    public boolean dispatchGenericMotionEvent(MotionEvent ev) {
        switch(ev.getAction()) {
        case MotionEvent.ACTION_MOVE:
            setAxisValue(AXIS_MOVE_FB, applyDeadZone(-ev.getAxisValue(MotionEvent.AXIS_Y)));
            setAxisValue(AXIS_MOVE_LR, applyDeadZone(-ev.getAxisValue(MotionEvent.AXIS_X)));
            setAxisValue(AXIS_LOOK_LR, applyDeadZone(-ev.getAxisValue(MotionEvent.AXIS_Z)) * MULT_VIEW_CONTROLLER * ctrlAimSensibility);
            setAxisValue(AXIS_LOOK_UD, applyDeadZone(-ev.getAxisValue(MotionEvent.AXIS_RZ)) * MULT_VIEW_CONTROLLER * ctrlAimSensibility);
            nDispatchKeyEvent(KeyEvent.KEYCODE_BUTTON_R2, ev.getAxisValue(MotionEvent.AXIS_RTRIGGER) > .5f ? 1 : 0);
            nDispatchKeyEvent(KeyEvent.KEYCODE_BUTTON_L2, ev.getAxisValue(MotionEvent.AXIS_RTRIGGER) < -.5f ? 1 : 0);
            nDispatchKeyEvent(KeyEvent.KEYCODE_DPAD_LEFT, ev.getAxisValue(MotionEvent.AXIS_HAT_X) < -.5f ? 1 : 0);
            nDispatchKeyEvent(KeyEvent.KEYCODE_DPAD_RIGHT, ev.getAxisValue(MotionEvent.AXIS_HAT_X) > .5f ? 1 : 0);
            nDispatchKeyEvent(KeyEvent.KEYCODE_DPAD_UP, ev.getAxisValue(MotionEvent.AXIS_HAT_Y) < -.5f ? 1 : 0);
            nDispatchKeyEvent(KeyEvent.KEYCODE_DPAD_DOWN, ev.getAxisValue(MotionEvent.AXIS_HAT_Y) > .5f ? 1 : 0);
			break;
		default:
			return false;
        }
        return true;
    }

    private float applyDeadZone(float input) {
        if (input < -deadZone) {
            return (input + deadZone) / (1 - deadZone);
        } else if (input > deadZone) {
            return (input - deadZone) / (1 - deadZone);
        } else {
            return 0;
        }
    }

    public static void tryPremain(Context context) {
        if (hasStoragePermission(context)) {
            File homeDir = getHomeDir();
            if (!homeDir.exists()) homeDir.mkdirs();
            SeriousSamSurface.initializeLibrary(homeDir.getAbsolutePath(), getLibDir(context));
        }
    }

    @NonNull
    private static String getLibDir(Context context) {
        return context.getApplicationInfo().dataDir + "/lib";
    }

    @NonNull
    private static File getHomeDir() {
        return new File(Environment.getExternalStorageDirectory(), BuildConfig.home).getAbsoluteFile();
    }

    @Override
    public boolean dispatchKeyEvent(KeyEvent event) {
        int keyCode = event.getKeyCode();
		int deviceId = event.getDeviceId();
        int source = event.getSource();
		Log.i(TAG, "Source: " + source);
       /* if (event.getRepeatCount() == 0 && ((source & InputDevice.SOURCE_KEYBOARD) != 0)) {
			executeShell("input_iIsShiftPressed = " + (event.isShiftPressed() ? 1 : 0));
			switch (event.getAction()) {
				case KeyEvent.ACTION_DOWN:
					//if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
					//	executeShell("sam_bMenu=1;");
					//}
					//nDispatchKeyEvent(keyCode, 1);
					nSendKeyboardButtonDown(keyCode);
					
					break;
				case KeyEvent.ACTION_UP:
				
					nSendKeyboardButtonUp(keyCode);
					//nDispatchKeyEvent(keyCode, 0);
					break;
				default:
					return false;
			}
        } */
		if ((source & InputDevice.SOURCE_MOUSE) != 0) {
            if ((keyCode == KeyEvent.KEYCODE_FORWARD)) {
                switch (event.getAction()) {
                case KeyEvent.ACTION_DOWN:
                case KeyEvent.ACTION_UP:
                    return true;
                }
            }
        }
		if (keyCode == KeyEvent.KEYCODE_VOLUME_UP || keyCode == KeyEvent.KEYCODE_VOLUME_DOWN) {
			return false;
		}
		if (gameState == GameState.MENU || gameState == GameState.CONSOLE) {
			executeShell("input_iIsShiftPressed = " + (event.isShiftPressed() ? 1 : 0));
			if (event.getAction() == KeyEvent.ACTION_DOWN) {

				System.out.println(" KEYCODE=" +  keyCode);

				switch (keyCode) {
					case KeyEvent.KEYCODE_DPAD_DOWN:
						executeShell("MenuEvent(" + VK_DOWN + ")");
						break;
					case KeyEvent.KEYCODE_DPAD_RIGHT:
						executeShell("MenuEvent(" + VK_RIGHT + ")");
						break;
					case KeyEvent.KEYCODE_DPAD_UP:
						executeShell("MenuEvent(" + VK_UP + ")");
						break;
					case KeyEvent.KEYCODE_DPAD_LEFT:
						executeShell("MenuEvent(" + VK_LEFT + ")");
						break;
					case KeyEvent.KEYCODE_MOVE_HOME:
						executeShell("MenuEvent(" + VK_HOME + ")");
						break;
					case KeyEvent.KEYCODE_MOVE_END:
						executeShell("MenuEvent(" + VK_END + ")");
						break;
					case KeyEvent.KEYCODE_DEL:
						executeShell("MenuEvent(" + VK_BACK + ")");
						break;
					case KeyEvent.KEYCODE_FORWARD_DEL:
						executeShell("MenuEvent(" + VK_DELETE + ")");
						break;
					case KeyEvent.KEYCODE_ENTER:
					case KeyEvent.KEYCODE_NUMPAD_ENTER:
					case KeyEvent.KEYCODE_BUTTON_A:
						executeShell("MenuEvent(" + VK_RETURN + ")");
						break;
					case KeyEvent.KEYCODE_BUTTON_B:
					case KeyEvent.KEYCODE_ESCAPE:
					case KeyEvent.KEYCODE_BACK:
						executeShell("GoMenuBack()");
						break;
					case KeyEvent.KEYCODE_F1:
						executeShell("MenuEvent(" + VK_F1 + ")");
						break;
					case KeyEvent.KEYCODE_F2:
						executeShell("MenuEvent(" + VK_F2 + ")");
						break;
					case KeyEvent.KEYCODE_F3:
						executeShell("MenuEvent(" + VK_F3 + ")");
						break;
					case KeyEvent.KEYCODE_F4:
						executeShell("MenuEvent(" + VK_F4 + ")");
						break;
					case KeyEvent.KEYCODE_F5:
						executeShell("MenuEvent(" + VK_F5 + ")");
						break;
					case KeyEvent.KEYCODE_F6:
						executeShell("MenuEvent(" + VK_F6 + ")");
						break;
					case KeyEvent.KEYCODE_F7:
						executeShell("MenuEvent(" + VK_F7 + ")");
						break;
					case KeyEvent.KEYCODE_F8:
						executeShell("MenuEvent(" + VK_F8 + ")");
						break;
					case KeyEvent.KEYCODE_F9:
						executeShell("MenuEvent(" + VK_F9 + ")");
						break;
					case KeyEvent.KEYCODE_F10:
						executeShell("MenuEvent(" + VK_F10 + ")");
						break;
					case KeyEvent.KEYCODE_F11:
						executeShell("MenuEvent(" + VK_F11 + ")");
						break;
					case KeyEvent.KEYCODE_F12:
						executeShell("MenuEvent(" + VK_F12 + ")");
						break;
					case KeyEvent.KEYCODE_TAB:
						executeShell("MenuEvent(" + VK_TAB + ")");
						break;
					case KeyEvent.KEYCODE_PAGE_UP:
						executeShell("MenuEvent(" + VK_PRIOR + ")");
						break;
					case KeyEvent.KEYCODE_PAGE_DOWN:
						executeShell("MenuEvent(" + VK_NEXT + ")");
						break;
				}
				if (event.getRepeatCount() == 0 && gameState == GameState.CONSOLE && keyCode == KeyEvent.KEYCODE_BUTTON_START) {
					executeShell("HideConsole();");
					keyboardHeightProvider.hideKeyboard();
				}
			}
		} else if (gameState == GameState.COMPUTER) {
			if (event.getRepeatCount() == 0 && event.getAction() == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_BACK) {
				executeShell("HideComputer();");
			}
		} else if (gameState != GameState.INTRO) {
			if (event.getRepeatCount() == 0) {
				if (event.getAction() == KeyEvent.ACTION_DOWN) {
					if (keyCode == KeyEvent.KEYCODE_ESCAPE) {
						executeShell("sam_bMenu=1;");
					}
					nDispatchKeyEvent(keyCode, 1);
				}
				if (event.getAction() == KeyEvent.ACTION_UP) {
					nDispatchKeyEvent(keyCode, 0);
				}
			}
		}
		int unicodeChar = event.getUnicodeChar();
		if (event.getAction() == KeyEvent.ACTION_DOWN && unicodeChar > 0) {
			executeShell("MenuChar(" + unicodeChar + ")");
		}
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == REQUEST_WRITE_STORAGE) {
            if (grantResults.length > 0) {
                if (grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                Log.d(TAG,"Permission is granted");
                    startGame();
                } else {
                    finish();
                }
            }
        }
    }

    // ui listeners
    public void showMenu(View view) {
        executeShell("sam_bMenu=1;");
	executeShell("net_WifiIP=\""+getWifiIP()+"\"");
    }

    public void doProfiling(View view) {
        executeShell("RecordProfile();");
    }

    public void doConsole(View view) {
        executeShell("ToggleConsole();");
    }

    public void keyboardHidden() {
        executeShell("HideConsole();");
    }

    public void doQuickLoad(View view) {
        executeShell("gam_bQuickLoad=1;");
    }

    public void doQuickSave(View view) {
        executeShell("gam_bQuickSave=1;");
    }
	
    public void btnApply(View view) {
		Toast toast = Toast.makeText(MainActivity.this, "Buttons mapping: OFF",Toast.LENGTH_SHORT);
		toast.show();
		findViewById(R.id.buttonApply).setVisibility(View.GONE);
		findViewById(R.id.buttonPlus).setVisibility(View.GONE);
		findViewById(R.id.buttonMinus).setVisibility(View.GONE);
		findViewById(R.id.input_SeriousBomb).setVisibility(View.GONE);
		ButtonsMapping = false;
    }
	
	public void btnPlus(View view) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
		SharedPreferences.Editor sharedPreferencesEditor = preferences.edit();
		int[] id = new int[] {R.id.input_use, R.id.input_crunch, R.id.input_jump, R.id.buttonPrev, R.id.buttonNext,R.id.input_fire, R.id.input_SeriousBomb};
		String[] buttonIdName = new String[] {"input_use", "input_crunch", "input_jump", "buttonPrev",
										   "buttonNext", "input_fire", "input_SeriousBomb"};

		for(int i=0; i<id.length; i++) {
			findViewById(id[i]).getLayoutParams().width = findViewById(id[i]).getLayoutParams().width + 5;
			findViewById(id[i]).getLayoutParams().height = findViewById(id[i]).getLayoutParams().height + 5;
			sharedPreferencesEditor.putInt(buttonIdName[i]+"H", findViewById(id[i]).getLayoutParams().height).apply();
			sharedPreferencesEditor.putInt(buttonIdName[i]+"W", findViewById(id[i]).getLayoutParams().width).apply();
			findViewById(id[i]).setVisibility(View.GONE);
			findViewById(id[i]).setVisibility(View.VISIBLE);
			
		}
		sharedPreferencesEditor.putFloat("input_overlayH", findViewById(R.id.input_overlay).getLayoutParams().width).apply();
		sharedPreferencesEditor.putFloat("input_overlayW", findViewById(R.id.input_overlay).getLayoutParams().width).apply();
	}
	
	public void btnMinus(View view) {
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
		SharedPreferences.Editor sharedPreferencesEditor = preferences.edit();
		int[] id = new int[] {R.id.input_use, R.id.input_crunch, R.id.input_jump, R.id.buttonPrev, R.id.buttonNext,R.id.input_fire, R.id.input_SeriousBomb};
		String[] buttonIdName = new String[] {"input_use", "input_crunch", "input_jump", "buttonPrev",
										   "buttonNext", "input_fire", "input_SeriousBomb"};
										   
		for(int i=0; i<id.length; i++) {
			findViewById(id[i]).getLayoutParams().width = findViewById(id[i]).getLayoutParams().width - 5;
			findViewById(id[i]).getLayoutParams().height = findViewById(id[i]).getLayoutParams().height - 5;
			sharedPreferencesEditor.putInt(buttonIdName[i]+"H", findViewById(id[i]).getLayoutParams().height).apply();
			sharedPreferencesEditor.putInt(buttonIdName[i]="W", findViewById(id[i]).getLayoutParams().width).apply();
			findViewById(id[i]).setVisibility(View.GONE);
			findViewById(id[i]).setVisibility(View.VISIBLE);
		}	
		sharedPreferencesEditor.putFloat("input_overlayH", findViewById(R.id.input_overlay).getLayoutParams().width).apply();
		sharedPreferencesEditor.putFloat("input_overlayW", findViewById(R.id.input_overlay).getLayoutParams().width).apply();
	}

    @Override
    public void onWindowFocusChanged(boolean hasFocus) {
        super.onWindowFocusChanged(hasFocus);
        if (hasFocus) {
            updateSoftKeyboardVisible();
        }
    }

    private void startGame() {
        if (!homeDir.exists()) homeDir.mkdirs();
        try {
            copyFolder("Scripts/Menu");
            copyFolder("Scripts/NetSettings");
            copyFolder("Classes/AdvancedItemClasses");
            copyFolder("Classes/AdvancedMonsterClasses");
        } catch (IOException e) {
            Log.e(TAG, "Error while copying resources", e);
        }
        SeriousSamSurface.initializeLibrary(homeDir.getAbsolutePath(), getLibDir(this));
        isGameStarted = true;
        glSurfaceView.start();
    }

    public void syncOptions() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(this);
        useGyroscope = preferences.getBoolean("use_gyroscope", false);
        showTouchController = preferences.getString("showTouchController", "Auto");
        gyroSensibility = preferences.getInt("gyro_sensibility", 50) / 100.f;
        aimViewSensibility = preferences.getInt("aimView_sensibility", 100) / 100.f;
        ctrlAimSensibility = preferences.getInt("ctrl_aimSensibility", 100) / 100.f;
        deadZone = preferences.getInt("ctrl_deadZone", 20) / 100.f;
        din_uiScale = preferences.getString("din_uiScale", "On");
        ui_drawBanner = preferences.getString("ui_drawBanner", "On");
        DinamicUI();
		drawBanner();
        executeShell("hud_iStats=" + (preferences.getBoolean("hud_iStats", false) ? 2 : 0) + ";");
        executeShell("input_uiScale=" + uiScale + ";");
        Log.i(TAG, "uiScale: " + uiScale);
        updateSoftKeyboardVisible();
    }
	
    @Subscribe(threadMode = ThreadMode.MAIN)
	public void updateUI(UpdateUIEvent event) {
        final MainActivity context = MainActivity.this;
        runOnUiThread(new Runnable() {
            public void run() {		
			
		SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
		
		int[] buttonId = new int[] {R.id.input_use, R.id.input_crunch, R.id.input_jump, R.id.buttonPrev, R.id.buttonNext,R.id.input_fire, R.id.input_SeriousBomb, R.id.input_overlay};
		String[] buttonIdName = new String[] {"input_use", "input_crunch", "input_jump", "buttonPrev",
										   "buttonNext", "input_fire", "input_SeriousBomb", "input_overlay"};

		for(int i=0; i<buttonId.length; i++) {
			float X = preferences.getFloat(buttonIdName[i]+"X",findViewById(buttonId[i]).getX());
			float Y = preferences.getFloat(buttonIdName[i]+"Y",findViewById(buttonId[i]).getY());
			findViewById(buttonId[i]).setX(X);
			findViewById(buttonId[i]).setY(Y);
			int H = preferences.getInt(buttonIdName[i]+"H",findViewById(buttonId[i]).getLayoutParams().height);
			int W = preferences.getInt(buttonIdName[i]+"W",findViewById(buttonId[i]).getLayoutParams().width);
			findViewById(buttonId[i]).getLayoutParams().height = H;
			findViewById(buttonId[i]).getLayoutParams().width = W;
		}
            }
        });
	}
	
    private class MyBtnListener implements View.OnTouchListener {
        float lastX, lastY;
        private int btnToBind;
	SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(MainActivity.this);
    SharedPreferences.Editor sharedPreferencesEditor = preferences.edit();
	
        public MyBtnListener() {
            this.btnToBind = 0;
        }

        public MyBtnListener(int btnToBind) {
            this.btnToBind = btnToBind;
        }
		
        float dX, dY;

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            String fullName = getResources().getResourceName(v.getId());
            String name = fullName.substring(fullName.lastIndexOf("/") + 1);
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    if (ButtonsMapping && (!name.equals("bgTrackerView"))){
                        isTracking = false;
                        dX = v.getX() - event.getRawX();
                        dY = v.getY() - event.getRawY();
						currentView = v;
                    } else {
                        isTracking = true;
                        lastX = event.getX();
                        lastY = event.getY();
                        if (this.btnToBind != 0) {
                            nDispatchKeyEvent(btnToBind, 1);
                        }
                    }
                    break;
                case MotionEvent.ACTION_UP:
                    if (this.btnToBind != 0) {
                        nDispatchKeyEvent(btnToBind, 0);
                    }
                    break;
                case MotionEvent.ACTION_POINTER_UP:
                    isTracking = false;
                    break;
                case MotionEvent.ACTION_MOVE:
                    if (isTracking) {
                        float rawX = event.getX();
                        float rawY = event.getY();
                        shiftAxisValue(AXIS_LOOK_LR, -Utils.convertPixelsToDp(rawX - lastX, MainActivity.this) * MULT_VIEW_TRACKER * aimViewSensibility);
                        shiftAxisValue(AXIS_LOOK_UD, -Utils.convertPixelsToDp(rawY - lastY, MainActivity.this) * MULT_VIEW_TRACKER * aimViewSensibility);
                        lastX = rawX;
                        lastY = rawY;
                    } else if (ButtonsMapping) {
                    v.setX(event.getRawX() + dX - -Utils.convertPixelsToDp(v.getWidth() / 2, MainActivity.this));
                    v.setY(event.getRawY() + dY - -Utils.convertPixelsToDp(v.getHeight() / 2, MainActivity.this));
                    currentView = v;
					String[] buttonId = new String[] {"input_use", "input_crunch", "input_jump", "buttonPrev", 
													   "buttonNext", "input_fire", "input_SeriousBomb"};
					for(int i=0; i<buttonId.length; i++) {
						if (name.equals(buttonId[i])) {
					sharedPreferencesEditor.putFloat(buttonId[i]+"X", v.getX()).apply();
					sharedPreferencesEditor.putFloat(buttonId[i]+"Y", v.getY()).apply();
						}
					}
                }
                    break;
                default:
                    return false;
				}
           return true;
        }
    }

    public static void executeShell(String command) {
        nShellExecute(command);
    }

    private static native void setAxisValue(int key, float value);
    private static native void shiftAxisValue(int key, float value);
    private static native void nShellExecute(String command);
    private static native void nDispatchKeyEvent(int key, int isPressed);
    private static native void nConfirmEditText(String newText);
    private static native void nCancelEditText();
	private static native void nSendMouseNative(int button, int action, float scroll);
	private static native void nSendKeyboardButtonDown(int button);
	private static native void nSendKeyboardButtonUp(int button);
}
