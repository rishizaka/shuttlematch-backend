package com.shuttlematch.domain.model.circle;

import com.shuttlematch.domain.model.user.UserId;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * サークル(集約ルート)。メンバーを保持する。作成者は ORGANIZER として自動的にメンバーになる。
 */
public class Circle {

    private final CircleId id;
    private String name;
    private String description;
    private final InviteCode inviteCode;
    private JoinPolicy joinPolicy;
    private final UserId createdBy;
    private final List<Member> members;

    private Circle(
            CircleId id, String name, String description, InviteCode inviteCode,
            JoinPolicy joinPolicy, UserId createdBy, List<Member> members) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.inviteCode = inviteCode;
        this.joinPolicy = joinPolicy;
        this.createdBy = createdBy;
        this.members = members;
    }

    public static Circle create(String name, String description, JoinPolicy joinPolicy, UserId createdBy) {
        Objects.requireNonNull(createdBy, "createdBy は必須です");
        Objects.requireNonNull(joinPolicy, "joinPolicy は必須です");
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("name は必須です");
        }
        List<Member> members = new ArrayList<>();
        members.add(new Member(createdBy, MemberRole.ORGANIZER));
        return new Circle(CircleId.newId(), name, description, InviteCode.generate(),
                joinPolicy, createdBy, members);
    }

    public static Circle reconstitute(
            CircleId id, String name, String description, InviteCode inviteCode,
            JoinPolicy joinPolicy, UserId createdBy, List<Member> members) {
        return new Circle(id, name, description, inviteCode, joinPolicy, createdBy,
                new ArrayList<>(members));
    }

    /** メンバーを追加する。既に参加済みのユーザーは追加できない。 */
    public Member addMember(UserId userId, MemberRole role) {
        Objects.requireNonNull(userId, "userId は必須です");
        boolean already = members.stream().anyMatch(m -> userId.equals(m.userId()));
        if (already) {
            throw new IllegalArgumentException("既にこのサークルのメンバーです");
        }
        Member member = new Member(userId, role);
        members.add(member);
        return member;
    }

    public List<Member> members() {
        return List.copyOf(members);
    }

    public CircleId id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public InviteCode inviteCode() {
        return inviteCode;
    }

    public JoinPolicy joinPolicy() {
        return joinPolicy;
    }

    public UserId createdBy() {
        return createdBy;
    }
}
