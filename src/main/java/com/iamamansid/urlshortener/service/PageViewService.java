package com.iamamansid.urlshortener.service;

import com.iamamansid.urlshortener.entity.PageView;
import com.iamamansid.urlshortener.repository.PageViewRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Records homepage visits for the admin "site visitors" chart. Tracking is
 * best-effort: failures are swallowed so a stats write can never break the
 * page itself.
 */
@Service
public class PageViewService {

    private static final Logger log = LoggerFactory.getLogger(PageViewService.class);

    private final PageViewRepository views;

    public PageViewService(PageViewRepository views) {
        this.views = views;
    }

    @Transactional
    public void record(String path) {
        try {
            PageView view = new PageView();
            view.setPath(path);
            views.save(view);
        } catch (Exception e) {
            log.warn("Failed to record page view for {}", path, e);
        }
    }
}
