package cloyster.final_project_cloyster;

import android.app.Application;
import android.content.res.Configuration;
import android.content.res.Resources;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.design.widget.Snackbar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.View;

import java.util.Locale;

public class FM extends Application {

    @Override
    public void onCreate() {
        initLanguageDebugMode("en");
        super.onCreate();
    }

    /**
     * Forces the app to use a specific language for development and debugging purposes.
     * @param language ISO 639 alpha-2 or alpha-3 language code. @see <a href="https://developer.android.com/reference/java/util/Locale.html">Locale</a>
     */
    private void initLanguageDebugMode(String language) {
        Locale locale = new Locale(language);
        Locale.setDefault(locale);

        Configuration config = new Configuration();
        config.setLocale(locale);

        Resources res = getApplicationContext().getResources();
        res.updateConfiguration(config, res.getDisplayMetrics());
    }

}
