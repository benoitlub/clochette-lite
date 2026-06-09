const hiddenCompatIds = [
  { id: "manualPingBtn", tag: "button" },
  { id: "noteBtn", tag: "button" },
  { id: "activityLog", tag: "ul" },
  { id: "privateNote", tag: "div" }
];

hiddenCompatIds.forEach(({ id, tag }) => {
  if (document.getElementById(id)) return;
  const el = document.createElement(tag);
  el.id = id;
  el.hidden = true;
  el.setAttribute("aria-hidden", "true");
  el.style.display = "none";
  document.body.appendChild(el);
});
