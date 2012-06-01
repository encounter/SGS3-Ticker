package com.encounterpc.ticker;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.net.Uri;
import android.os.Bundle;
import android.text.Html;
import android.view.*;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.widget.HorizontalScrollView;
import android.widget.TextView;
import org.mcsoxford.rss.RSSFeed;
import org.mcsoxford.rss.RSSItem;
import org.mcsoxford.rss.RSSReader;

import java.text.SimpleDateFormat;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class MainActivity extends Activity {
    private LayoutInflater inflater;

    public final static int TICKER_SPEED_FAST = 3;
    public final static int TICKER_SPEED_MEDIUM = 2;
    public final static int TICKER_SPEED_SLOW = 1;

    public final static String DATE_FORMAT = "MM/dd";
    public final static String TIME_FORMAT = "hh:mm aa";

    public final static int REFRESH_MINUTES = 30;

    private HorizontalScrollView ticker;
    private ViewGroup tickerContent;
    private ViewGroup info;

    private Thread tickerThread;
    private Thread tickerResume;
    private boolean tickerActive;

    private Uri currentFeed = Uri.parse("http://feeds.gawker.com/gizmodo/full");
    private boolean feedUpdating;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.main);

        ticker = (HorizontalScrollView) findViewById(R.id.ticker);
        tickerContent = (ViewGroup) findViewById(R.id.tickerContent);
        info = (ViewGroup) findViewById(R.id.info);
        info.getBackground().setDither(true);
        inflater = (LayoutInflater) getSystemService(LAYOUT_INFLATER_SERVICE);

        updateFeed();

        tickerThread = new Thread() {
            @Override
            public void run() {
                while (true) {
                    try {
                        sleep(20);
                    } catch (InterruptedException ignored) {
                        break;
                    }
                    if (!tickerActive) continue;
                    ThreadUtil.runOnUiThread(new Runnable() {
                        @Override
                        public void run() {
                            ticker.scrollBy(TICKER_SPEED_FAST, 0);
                            if (checkTickerEnd()) {
                                stopTicker();
                                startTicker();
                            }
                        }
                    });
                }
            }
        };

        ticker.setOnTouchListener(new View.OnTouchListener() {
            @Override
            public boolean onTouch(View view, MotionEvent motionEvent) {
                if (motionEvent.getAction() == MotionEvent.ACTION_DOWN) {
                    stopTicker();
                } else if (motionEvent.getAction() == MotionEvent.ACTION_UP) {
                    stopTicker();
                    startTicker();
                }
                return false;
            }
        });

        findViewById(R.id.refresh).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                updateFeed();
            }
        });

        findViewById(R.id.choosefeed).setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                Map<String, Uri> feeds = new LinkedHashMap<String, Uri>();
                feeds.put("Gizmodo", Uri.parse("http://feeds.gawker.com/gizmodo/full"));
                feeds.put("Lifehacker", Uri.parse("http://feeds.gawker.com/lifehacker/full"));
                feeds.put("RootzWiki", Uri.parse("http://rootzwiki.com/rss/ccs/1-rootzwiki/"));
                feeds.put("Yahoo! News", Uri.parse("http://news.yahoo.com/rss/"));

                String[] keys = Arrays.copyOf(feeds.keySet().toArray(), feeds.size(), String[].class);

                int selected = -1;
                final List<Uri> values = new ArrayList<Uri>(feeds.values());
                if (values.contains(currentFeed))
                    selected = values.indexOf(currentFeed);

                new AlertDialog.Builder(MainActivity.this)
                        .setTitle("Select feed")
                        .setSingleChoiceItems(keys, selected, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int item) {
                                currentFeed = values.get(item);
                                updateFeed();
                                dialog.dismiss();
                            }
                        }).create().show();
            }
        });
    }

    private void fadeOut(final View view, final int duration, final Runnable runnable) {
        ThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlphaAnimation animation = new AlphaAnimation(1, 0);
                animation.setDuration(duration);
                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        view.setVisibility(View.GONE);
                        view.clearAnimation();
                        if (runnable != null)
                            runnable.run();
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                view.clearAnimation();
                view.startAnimation(animation);
            }
        });
    }

    private void fadeIn(final View view, final int duration, final Runnable runnable) {
        ThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                AlphaAnimation animation = new AlphaAnimation(0, 1);
                animation.setDuration(duration);
                animation.setAnimationListener(new Animation.AnimationListener() {
                    @Override
                    public void onAnimationStart(Animation animation) {
                    }

                    @Override
                    public void onAnimationEnd(Animation animation) {
                        view.clearAnimation();
                        if (runnable != null)
                            runnable.run();
                    }

                    @Override
                    public void onAnimationRepeat(Animation animation) {
                    }
                });
                view.setVisibility(View.VISIBLE);
                view.clearAnimation();
                view.startAnimation(animation);
            }
        });
    }

    private void updateFeed() {
        if (!feedUpdating) {
            feedUpdating = true;
            if (ticker.getVisibility() == View.VISIBLE) {
                stopTicker();
                fadeOut(ticker, 500, new Runnable() {
                    @Override
                    public void run() {
                        tickerContent.removeAllViews();
                        fadeIn(findViewById(R.id.refreshContainer), 500, null);
                    }
                });
            }
            new Thread() {
                public void run() {
                    try {
                        sleep(1000); // hack to let animations finish TODO: fix?
                    } catch (InterruptedException ignored) {
                    }
                    try {
                        final Pattern pattern = Pattern.compile("^.+\\[(.+)\\]$");
                        RSSReader reader = new RSSReader();
                        final RSSFeed feed = reader.load(currentFeed.toString());
                        final List<RSSItem> items = feed.getItems();
                        ThreadUtil.runOnUiThread(new Runnable() {
                            @Override
                            public void run() {
                                for (int i = 0, itemsSize = 10; i < itemsSize; i++) {
                                    final RSSItem item = items.get(i);
                                    ViewGroup tickerItem = (ViewGroup) inflater.inflate(R.layout.ticker_article, null);
                                    String title = Html.fromHtml(item.getTitle()).toString();
                                    Matcher m = pattern.matcher(title);
                                    if (m.find()) {
                                        ((TextView) tickerItem.findViewById(R.id.source)).setText(m.group(1));
                                        tickerItem.findViewById(R.id.source).setVisibility(View.VISIBLE);
                                        ((TextView) tickerItem.findViewById(R.id.content)).setText(title.replaceFirst("[ \\t]*\\[.+\\]$", ""));
                                    } else
                                        ((TextView) tickerItem.findViewById(R.id.content)).setText(title);
                                    Calendar c1 = Calendar.getInstance();
                                    Calendar c2 = Calendar.getInstance();
                                    c2.setTime(item.getPubDate());
                                    boolean isToday = c1.get(Calendar.YEAR) == c2.get(Calendar.YEAR)
                                            && c1.get(Calendar.DAY_OF_YEAR) == c2.get(Calendar.DAY_OF_YEAR);
                                    SimpleDateFormat dateFormat = new SimpleDateFormat((!isToday ? DATE_FORMAT + " " : "") + TIME_FORMAT);
                                    ((TextView) tickerItem.findViewById(R.id.time)).setText(dateFormat.format(item.getPubDate()));
                                    if (item.getSourceName() != null) {
                                        ((TextView) tickerItem.findViewById(R.id.source)).setText(item.getSourceName());
                                        if (item.getSourceUrl() != null) {
                                            tickerItem.findViewById(R.id.source).setOnClickListener(new View.OnClickListener() {
                                                @Override
                                                public void onClick(View view) {
                                                    startActivity(new Intent(Intent.ACTION_VIEW, item.getSourceUrl()));
                                                }
                                            });
                                        }
                                        tickerItem.findViewById(R.id.source).setVisibility(View.VISIBLE);
                                    }
                                    tickerItem.setOnClickListener(new View.OnClickListener() {
                                        @Override
                                        public void onClick(View view) {
                                            startActivity(new Intent(Intent.ACTION_VIEW, item.getLink()));
                                        }
                                    });
                                    tickerContent.addView(tickerItem);
                                }
                                ((TextView) findViewById(R.id.feedTitle)).setText(feed.getTitle());
                                ((TextView) findViewById(R.id.refreshTime)).setText(new SimpleDateFormat(DATE_FORMAT + " " + TIME_FORMAT).format(new Date()));
                            }
                        });
                        ticker.scrollTo(0, 0);
                        fadeOut(findViewById(R.id.refreshContainer), 500, new Runnable() {
                            @Override
                            public void run() {
                                fadeIn(ticker, 500, null);
                            }
                        });
                        if (!tickerThread.isAlive())
                            tickerThread.start();
                        startTicker();
                    } catch (Exception e) { // TODO: better error handling
                        e.printStackTrace();
                    }
                    feedUpdating = false;
                }
            }.start();
        }
    }

    private void stopTicker() {
        tickerActive = false;
        if (tickerResume != null) {
            tickerResume.interrupt();
            tickerResume = null;
        }
    }

    private void startTicker() {
        (tickerResume = new Thread() {
            @Override
            public void run() {
                try {
                    sleep(1500);
                } catch (InterruptedException e) {
                    return;
                }
                if (checkTickerEnd()) {
                    resetTicker();
                    run();
                } else
                    tickerActive = true;
            }
        }).start();
    }

    // getMaxScroll doesn't work in this situation
    private boolean checkTickerEnd() {
        return ticker.getScrollX() >= (tickerContent.getWidth() - getWindow().getDecorView().getWidth());
    }

    private void resetTicker() {
        ThreadUtil.runOnUiThread(new Runnable() {
            @Override
            public void run() {
                ticker.smoothScrollTo(0, 0);
            }
        });
    }

    @Override
    public void onPause() {
        super.onPause();
        stopTicker();
    }

    @Override
    public void onResume() {
        super.onResume();
        startTicker();
    }

    @Override
    public void onAttachedToWindow() {
        super.onAttachedToWindow();
        Window window = getWindow();
        window.setFormat(PixelFormat.RGBA_8888);
    }
}
