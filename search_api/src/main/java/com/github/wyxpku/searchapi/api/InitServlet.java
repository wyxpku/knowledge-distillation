package com.github.wyxpku.searchapi.api;

import com.github.wyxpku.searchapi.searcher.Searcher;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;


public class InitServlet extends HttpServlet {
    private static final long serialVersionUID = 1L;

    @Override
    public void init() throws ServletException {
        Searcher.getInstance().search("北京", 0, "", "");
    }


    @Override
    public void destroy() {
    }
}
