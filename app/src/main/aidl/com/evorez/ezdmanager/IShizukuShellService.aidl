package com.evorez.ezdmanager;

interface IShizukuShellService {
    void destroy() = 16777114;
    String execCommand(String command) = 1;
}
