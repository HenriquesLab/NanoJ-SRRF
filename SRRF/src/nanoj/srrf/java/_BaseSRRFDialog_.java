package nanoj.srrf.java;

import com.boxysystems.jgoogleanalytics.FocusPoint;
import com.boxysystems.jgoogleanalytics.JGoogleAnalyticsTracker;
import nanoj.core.java.gui._BaseDialog_;

import static nanoj.core.java.Version.headlessGetVersionSmall;

/**
 * Created with IntelliJ IDEA.
 * User: Ricardo Henriques <paxcalpt@gmail.com>
 * Date: 27/09/15
 * Time: 16:17
 */
public abstract class _BaseSRRFDialog_ extends _BaseDialog_ {

    protected JGoogleAnalyticsTracker tracker = new JGoogleAnalyticsTracker("NanoJ-SRRF", headlessGetVersionSmall(), "UA-61590656-3");
    protected FocusPoint parentFocusPoint;

    protected void track(String value) {
        if (parentFocusPoint==null) parentFocusPoint = new FocusPoint(getClassName());
        FocusPoint focus = new FocusPoint(value);
        focus.setParentTrackPoint(parentFocusPoint);
        tracker.trackAsynchronously(focus);
    }

    protected boolean isNewVersion() {
        return Version.isNewVersion() || nanoj.srrf.java.Version.isNewVersion();
    }

    protected String getWhatsNew() {
        return nanoj.srrf.java.Version.WHATS_NEW;
    }
}
