package com.shuttlematch.domain.model.session;

/**
 * 参加者の在席状態。
 */
public enum ParticipantStatus {
    /** 在席中(試合編成の対象)。 */
    ACTIVE,
    /** 早退(履歴は残すが、未開始セットの編成対象からは外す)。 */
    LEFT
}
