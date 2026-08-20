"use client";

import Link from "next/link";
import { useCallback, useEffect, useState } from "react";
import { AppShell } from "@/components/AppShell";
import { useSession } from "@/lib/store";
import { useToast } from "@/components/ToastProvider";
import {
  Badge,
  Button,
  Card,
  CardBody,
  CardHeader,
  EmptyState,
  Field,
  Input,
  Modal,
  Textarea,
} from "@/components/ui";
import { ApiError } from "@/lib/api";
import type { Project, TeamList } from "@/lib/types";
import { formatDate } from "@/lib/utils";

export default function DashboardPage() {
  const { api, teams, teamId, selectTeam, setTeams, token } = useSession();
  const { toast } = useToast();
  const [projects, setProjects] = useState<Project[]>([]);
  const [loading, setLoading] = useState(false);
  const [teamModal, setTeamModal] = useState(false);
  const [projectModal, setProjectModal] = useState(false);
  const [newTeamName, setNewTeamName] = useState("");
  const [newProjectName, setNewProjectName] = useState("");
  const [newProjectDesc, setNewProjectDesc] = useState("");
  const [creating, setCreating] = useState(false);

  const loadProjects = useCallback(async () => {
    if (!api.token || teamId == null) {
      setProjects([]);
      return;
    }
    setLoading(true);
    try {
      const list = await api.listProjects();
      setProjects(list);
    } catch (err) {
      const msg = err instanceof ApiError ? err.message : "Failed to load projects";
      toast(msg, "error");
    } finally {
      setLoading(false);
    }
  }, [api, teamId, toast]);

  const loadTeams = useCallback(async () => {
    if (!api.token) return;
    try {
      const t: TeamList = await api.listMyTeams();
      setTeams(t.items);
      if (t.items.length > 0 && teamId == null) {
        selectTeam(t.items[0].teamId);
      }
    } catch {
      /* backend offline */
    }
  }, [api, teamId, selectTeam, setTeams]);

  useEffect(() => {
    loadTeams();
  }, [loadTeams]);

  useEffect(() => {
    loadProjects();
  }, [loadProjects, teamId]);

  const handleCreateTeam = async () => {
    if (!newTeamName.trim()) return;
    setCreating(true);
    try {
      const team = await api.createTeam(newTeamName.trim());
      selectTeam(team.id);
      toast("Team created", "success");
      setTeamModal(false);
      setNewTeamName("");
      await loadTeams();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to create team", "error");
    } finally {
      setCreating(false);
    }
  };

  const handleCreateProject = async () => {
    if (!newProjectName.trim()) return;
    setCreating(true);
    try {
      await api.createProject({
        name: newProjectName.trim(),
        description: newProjectDesc.trim() || undefined,
      });
      toast("Project created", "success");
      setProjectModal(false);
      setNewProjectName("");
      setNewProjectDesc("");
      await loadProjects();
    } catch (err) {
      toast(err instanceof ApiError ? err.message : "Failed to create project", "error");
    } finally {
      setCreating(false);
    }
  };

  return (
    <AppShell>
      <div className="mb-6 flex items-center justify-between">
        <div>
          <h1 className="text-xl font-bold text-slate-900">Dashboard</h1>
          <p className="text-sm text-slate-500">Manage teams, projects and batches.</p>
        </div>
        <Button onClick={() => setProjectModal(true)} disabled={teamId == null}>
          + New project
        </Button>
      </div>

      {(!teams || teams.length === 0) && (
        <Card className="mb-6">
          <CardBody>
            <EmptyState
              icon="🏢"
              title="You are not in any team yet"
              description="Create a team to begin reviewing candidate videos. The creator becomes the team OWNER."
              action={<Button onClick={() => setTeamModal(true)}>Create team</Button>}
            />
          </CardBody>
        </Card>
      )}

      {teams && teams.length > 0 && (
        <Card className="mb-6">
          <CardHeader title="Your teams" subtitle="Switch the active team used for project scoping." />
          <CardBody>
            <div className="flex flex-wrap gap-2">
              {teams.map((t) => (
                <button
                  key={t.teamId}
                  onClick={() => selectTeam(t.teamId)}
                  className={
                    "rounded-lg border px-3 py-2 text-left transition " +
                    (teamId === t.teamId
                      ? "border-brand-500 bg-brand-50"
                      : "border-slate-200 bg-white hover:border-slate-300 hover:bg-slate-50")
                  }
                >
                  <div className="flex items-center gap-2">
                    <span className="text-sm font-semibold text-slate-800">{t.name}</span>
                    <Badge tone="purple">{t.role}</Badge>
                  </div>
                  <div className="mt-0.5 text-xs text-slate-400">#{t.teamId}</div>
                </button>
              ))}
              <Button variant="outline" size="sm" onClick={() => setTeamModal(true)}>
                + New team
              </Button>
            </div>
          </CardBody>
        </Card>
      )}

      <h2 className="mb-3 text-sm font-semibold text-slate-700">Projects</h2>

      {teamId == null ? (
        <Card>
          <CardBody>
            <EmptyState title="Select a team to see projects" />
          </CardBody>
        </Card>
      ) : projects.length === 0 && !loading ? (
        <Card>
          <CardBody>
            <EmptyState
              icon="📁"
              title="No projects yet"
              description="Create your first project, then define a quality profile and a batch."
              action={<Button onClick={() => setProjectModal(true)}>Create project</Button>}
            />
          </CardBody>
        </Card>
      ) : (
        <div className="grid gap-3 sm:grid-cols-2 lg:grid-cols-3">
          {projects.map((p) => (
            <Link key={p.id} href={"/projects/" + p.id}>
              <Card className="h-full transition hover:border-brand-400 hover:shadow-md">
                <CardBody>
                  <div className="flex items-start justify-between gap-2">
                    <h3 className="font-semibold text-slate-800">{p.name}</h3>
                    <Badge tone="slate">{p.status || "ACTIVE"}</Badge>
                  </div>
                  {p.description && (
                    <p className="mt-1 line-clamp-2 text-xs text-slate-500">{p.description}</p>
                  )}
                  <p className="mt-3 text-[11px] text-slate-400">
                    Updated {formatDate(p.updatedAt)}
                  </p>
                </CardBody>
              </Card>
            </Link>
          ))}
        </div>
      )}

      <Modal open={teamModal} onClose={() => setTeamModal(false)} title="Create team">
        <Field label="Team name" required hint="Min 1 character.">
          <Input
            value={newTeamName}
            onChange={(e) => setNewTeamName(e.target.value)}
            placeholder="My content studio"
            autoFocus
          />
        </Field>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={() => setTeamModal(false)}>
            Cancel
          </Button>
          <Button onClick={handleCreateTeam} loading={creating}>
            Create team
          </Button>
        </div>
      </Modal>

      <Modal open={projectModal} onClose={() => setProjectModal(false)} title="Create project">
        <div className="space-y-4">
          <Field label="Project name" required>
            <Input
              value={newProjectName}
              onChange={(e) => setNewProjectName(e.target.value)}
              placeholder="e.g. Summer sunscreen short-ad"
              autoFocus
            />
          </Field>
          <Field label="Description">
            <Textarea
              value={newProjectDesc}
              onChange={(e) => setNewProjectDesc(e.target.value)}
              rows={3}
              placeholder="Brand, audience, constraints…"
            />
          </Field>
        </div>
        <div className="mt-4 flex justify-end gap-2">
          <Button variant="outline" onClick={() => setProjectModal(false)}>
            Cancel
          </Button>
          <Button onClick={handleCreateProject} loading={creating}>
            Create project
          </Button>
        </div>
      </Modal>
    </AppShell>
  );
}
