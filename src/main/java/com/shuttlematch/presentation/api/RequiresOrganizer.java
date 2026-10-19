package com.shuttlematch.presentation.api;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * ルームの運営者だけが実行できる操作であることを示す。
 * <p>
 * 付いたハンドラは {@code X-Organizer-Token} ヘッダを必須とし、ルームの運営者トークンと
 * 一致しなければ 403 になる({@link com.shuttlematch.presentation.api.OrganizerAuthInterceptor})。
 * パスに {@code {roomId}} を含むハンドラにのみ付けられる。
 * <p>
 * 付けるのは「元に戻せない操作」だけ。早退・復帰のように運営者が後から戻せる操作は、
 * 本人も操作できるようにするため付けない。
 */
@Target(ElementType.METHOD)
@Retention(RetentionPolicy.RUNTIME)
@Documented
public @interface RequiresOrganizer {
}
