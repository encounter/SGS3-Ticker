/*
 * Copyright (C) 2010 A. Horn
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

package org.mcsoxford.rss;

import android.net.Uri;
import org.xml.sax.Attributes;

import java.util.HashMap;
import java.util.Map;

/**
 * Internal SAX handler to efficiently parse RSS feeds. Only a single thread
 * must use this SAX handler.
 *
 * @author Mr Horn
 */
class RSSHandler extends org.xml.sax.helpers.DefaultHandler {

    /**
     * Constant for XML element name which identifies RSS items.
     */
    private static final String RSS_ITEM = "item";

    /**
     * Constant symbol table to ensure efficient treatment of handler states.
     */
    private final Map<String, ContentSetter> contentSetters;
    private final Map<String, AttributeSetter> attributeSetters;

    /**
     * Reference is never {@code null}. Visibility must be package-private to
     * ensure efficiency of inner classes.
     */
    final RSSFeed feed = new RSSFeed();

    /**
     * Reference is {@code null} unless started to parse &lt;item&gt; element.
     * Visibility must be package-private to ensure efficiency of inner classes.
     */
    RSSItem item;

    /**
     * If not {@code null}, then buffer the characters inside an XML text element.
     */
    private StringBuilder buffer;

    /**
     * Dispatcher to set either {@link #feed} or {@link #item} fields.
     */
    private ContentSetter contentSetter;
    private AttributeSetter attributeSetter;

    /**
     * Interface to store information about RSS elements.
     */
    private static interface Setter {
    }

    /**
     * Closure to change fields in POJOs which store RSS content.
     */
    private static interface ContentSetter extends Setter {

        /**
         * Set the field of an object which represents an RSS element.
         */
        void set(String value);

    }

    /**
     * Closure to change fields in POJOs which store information
     * about RSS elements which have only attributes.
     */
    private static interface AttributeSetter extends Setter {

        /**
         * Set the XML attributes.
         */
        void set(org.xml.sax.Attributes attributes);

    }

    /**
     * Setter for RSS &lt;title&gt; elements inside a &lt;channel&gt; or an
     * &lt;item&gt; element. The title of the RSS feed is set only if
     * {@link #item} is {@code null}. Otherwise, the title of the RSS
     * {@link #item} is set.
     */
    private final ContentSetter SET_TITLE = new ContentSetter() {
        @Override
        public void set(String title) {
            if (item == null) {
                feed.setTitle(title);
            } else {
                item.setTitle(title);
            }
        }
    };

    /**
     * Setter for RSS &lt;description&gt; elements inside a &lt;channel&gt; or an
     * &lt;item&gt; element. The title of the RSS feed is set only if
     * {@link #item} is {@code null}. Otherwise, the title of the RSS
     * {@link #item} is set.
     */
    private final ContentSetter SET_DESCRIPTION = new ContentSetter() {
        @Override
        public void set(String description) {
            if (item == null) {
                feed.setDescription(description);
            } else {
                item.setDescription(description);
            }
        }
    };

    /**
     * Setter for an RSS &lt;content:encoded&gt; element inside an &lt;item&gt;
     * element.
     */
    private final ContentSetter SET_CONTENT = new ContentSetter() {
        @Override
        public void set(String content) {
            if (item != null) {
                item.setContent(content);
            }
        }
    };

    /**
     * Setter for RSS &lt;link&gt; elements inside a &lt;channel&gt; or an
     * &lt;item&gt; element. The title of the RSS feed is set only if
     * {@link #item} is {@code null}. Otherwise, the title of the RSS
     * {@link #item} is set.
     */
    private final ContentSetter SET_LINK = new ContentSetter() {
        @Override
        public void set(String link) {
            final android.net.Uri uri = android.net.Uri.parse(link);
            if (item == null) {
                feed.setLink(uri);
            } else {
                item.setLink(uri);
            }
        }
    };

    /**
     * Setter for RSS &lt;pubDate&gt; elements inside a &lt;channel&gt; or an
     * &lt;item&gt; element. The title of the RSS feed is set only if
     * {@link #item} is {@code null}. Otherwise, the title of the RSS
     * {@link #item} is set.
     */
    private final ContentSetter SET_PUBDATE = new ContentSetter() {
        @Override
        public void set(String pubDate) {
            final java.util.Date date = Dates.parseRfc822(pubDate);
            if (item == null) {
                feed.setPubDate(date);
            } else {
                item.setPubDate(date);
            }
        }
    };

    private final AttributeSetter SET_SOURCEURL = new AttributeSetter() {
        @Override
        public void set(Attributes attributes) {
            item.setSourceUrl(Uri.parse(attributes.getValue("url")));
        }
    };

    private final ContentSetter SET_SOURCENAME = new ContentSetter() {
        @Override
        public void set(String value) {
            if (item != null) {
                item.setSourceName(value);
            }
        }
    };

    /**
     * Setter for one or multiple RSS &lt;category&gt; elements inside a
     * &lt;channel&gt; or an &lt;item&gt; element. The title of the RSS feed is
     * set only if {@link #item} is {@code null}. Otherwise, the title of the RSS
     * {@link #item} is set.
     */
    private final ContentSetter ADD_CATEGORY = new ContentSetter() {

        @Override
        public void set(String category) {
            if (item == null) {
                feed.addCategory(category);
            } else {
                item.addCategory(category);
            }
        }
    };

    /**
     * Setter for one or multiple RSS &lt;media:thumbnail&gt; elements inside an
     * &lt;item&gt; element. The thumbnail element has only attributes. Both its
     * height and width are optional. Invalid elements are ignored.
     */
    private final AttributeSetter ADD_MEDIA_THUMBNAIL = new AttributeSetter() {

        private static final String MEDIA_THUMBNAIL_HEIGHT = "height";
        private static final String MEDIA_THUMBNAIL_WIDTH = "width";
        private static final String MEDIA_THUMBNAIL_URL = "url";
        private static final int DEFAULT_DIMENSION = -1;

        @Override
        public void set(org.xml.sax.Attributes attributes) {
            if (item == null) {
                // ignore invalid media:thumbnail elements which are not inside item
                // elements
                return;
            }

            final int height = MediaAttributes.intValue(attributes, MEDIA_THUMBNAIL_HEIGHT, DEFAULT_DIMENSION);
            final int width = MediaAttributes.intValue(attributes, MEDIA_THUMBNAIL_WIDTH, DEFAULT_DIMENSION);
            final String url = MediaAttributes.stringValue(attributes, MEDIA_THUMBNAIL_URL);

            if (url == null) {
                // ignore invalid media:thumbnail elements which have no URL.
                return;
            }

            item.addThumbnail(new MediaThumbnail(android.net.Uri.parse(url), height, width));
        }

    };

    /**
     * Use configuration to optimize initial capacities of collections
     */
    private final RSSConfig config;

    /**
     * Instantiate a SAX handler which can parse a subset of RSS 2.0 feeds.
     *
     * @param config configuration for the initial capacities of collections
     */
    RSSHandler(RSSConfig config) {
        this.config = config;

        // initialize dispatchers to manage the state of the SAX handler
        contentSetters = new java.util.HashMap<String, ContentSetter>();
        contentSetters.put("title", SET_TITLE);
        contentSetters.put("description", SET_DESCRIPTION);
        contentSetters.put("content:encoded", SET_CONTENT);
        contentSetters.put("link", SET_LINK);
        contentSetters.put("category", ADD_CATEGORY);
        contentSetters.put("pubDate", SET_PUBDATE);
        contentSetters.put("source", SET_SOURCENAME);

        attributeSetters = new HashMap<String, AttributeSetter>();
        attributeSetters.put("media:thumbnail", ADD_MEDIA_THUMBNAIL);
        attributeSetters.put("source", SET_SOURCEURL);
    }

    /**
     * Returns the RSS feed after this SAX handler has processed the XML document.
     */
    RSSFeed feed() {
        return feed;
    }

    /**
     * Identify the appropriate dispatcher which should be used to store XML data
     * in a POJO. Unsupported RSS 2.0 elements are currently ignored.
     */
    @Override
    public void startElement(String nsURI, String localName, String qname,
                             org.xml.sax.Attributes attributes) {
        // Lookup dispatcher in hash table
        contentSetter = contentSetters.get(qname);
        attributeSetter = attributeSetters.get(qname);
        if (contentSetter == null) {
            if (RSS_ITEM.equals(qname)) {
                item = new RSSItem(config.categoryAvg, config.thumbnailAvg);
            }
        } else {
            // Buffer supported RSS content data
            buffer = new StringBuilder();
        }
        if (attributeSetter != null) {
            attributeSetter.set(attributes);
        }
    }

    @Override
    public void endElement(String nsURI, String localName, String qname) {
        if (isBuffering()) {
            // set field of an RSS feed or RSS item
            contentSetter.set(buffer.toString());

            // clear buffer
            buffer = null;
        } else if (RSS_ITEM.equals(qname)) {
            feed.addItem(item);

            // (re)enter <channel> scope
            item = null;
        }
    }

    @Override
    public void characters(char ch[], int start, int length) {
        if (isBuffering()) {
            buffer.append(ch, start, length);
        }
    }

    /**
     * Determines if the SAX parser is ready to receive data inside an XML element
     * such as &lt;title&gt; or &lt;description&gt;.
     *
     * @return boolean {@code true} if the SAX handler parses data inside an XML
     *         element, {@code false} otherwise
     */
    boolean isBuffering() {
        return buffer != null && contentSetter != null;
    }

}

