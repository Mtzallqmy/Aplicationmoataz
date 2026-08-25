cmake_minimum_required(VERSION 3.22.1)
project(alaser_pty C)

add_library(alaser_pty SHARED pty.c)
target_compile_options(alaser_pty PRIVATE -Wall -Wextra -Werror)
