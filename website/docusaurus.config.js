// @ts-check
// Note: type annotations allow type checking and IDEs autocompletion

const lightCodeTheme = require('prism-react-renderer/themes/github');
const darkCodeTheme = require('prism-react-renderer/themes/dracula');

/** @type {import('@docusaurus/types').Config} */
const config = {
  title: 'Shiva',
  tagline: 'Vector Similarity Search made easy',
  favicon: 'img/favicon.ico',

  // Set the production url of your site here
  url: 'https://saucam.github.io',
  // Set the /<baseUrl>/ pathname under which your site is served
  // For GitHub pages deployment, it is often '/<projectName>/'
  baseUrl: '/shiva/',

  // GitHub pages deployment config.
  // If you aren't using GitHub pages, you don't need these.
  organizationName: 'saucam', // Usually your GitHub org/user name.
  projectName: 'shiva', // Usually your repo name.

  onBrokenLinks: 'throw',
  onBrokenMarkdownLinks: 'warn',

  // Even if you don't use internalization, you can use this field to set useful
  // metadata like html lang. For example, if your site is Chinese, you may want
  // to replace "en" with "zh-Hans".
  i18n: {
    defaultLocale: 'en',
    locales: ['en'],
  },

  presets: [
    [
      'classic',
      /** @type {import('@docusaurus/preset-classic').Options} */
      ({
        docs: {
	  path: '../shiva-docs/target/mdoc',
	  routeBasePath: 'docs',
          sidebarPath: require.resolve('./sidebars.js'),
          // Please change this to your repo.
          // Remove this to remove the "edit this page" links.
          // editUrl:
          //  'https://github.com/facebook/docusaurus/tree/main/packages/create-docusaurus/templates/shared/',
        },
        blog: false,
        theme: {
          customCss: require.resolve('./src/css/custom.css'),
        },
      }),
    ],
  ],

  themeConfig:
    /** @type {import('@docusaurus/preset-classic').ThemeConfig} */
    ({
      // Replace with your project's social card
      image: 'img/docusaurus-social-card.jpg',
      navbar: {
        title: 'Shiva',
        logo: {
          alt: 'My Site Logo',
          src: 'img/logo.svg',
        },
        hideOnScroll: true,
        items: [
          /** {
            type: 'docSidebar',
            sidebarId: 'tutorialSidebar',
            position: 'left',
            label: 'Tutorial',
          }, **/
          {
            type: 'doc',
            docId: 'README',
            label: 'Tutorial',
            position: 'left'
          },
          /**{to: '/blog', label: 'Blog', position: 'left'},**/
          {
            href: 'https://github.com/saucam/shiva',
            label: 'GitHub',
            position: 'right',
          },
          {
            href: 'https://saucam.github.io/shiva/api/index.html',
            label: 'Scaladoc',
            position: 'right',
          },
        ],
      },
      footer: {
        style: 'dark',
        links: [
          {
            title: 'Docs',
            items: [
              {
                label: 'Scaladoc',
                href: 'https://saucam.github.io/shiva/api/index.html',
              },
              {
                label: 'Tutorial',
                to: 'docs/readme',
              },
            ],
          },
          /** {
            title: 'Community',
            items: [
              {
                label: 'Stack Overflow',
                href: 'https://stackoverflow.com/questions/tagged/docusaurus',
              },
              {
                label: 'Discord',
                href: 'https://discordapp.com/invite/docusaurus',
              },
              {
                label: 'Twitter',
                href: 'https://twitter.com/docusaurus',
              },
            ],
          }, **/
          {
            title: 'More',
            items: [
              /** {
                label: 'Blog',
                to: '/blog',
              }, **/
              {
                label: 'GitHub',
                href: 'https://github.com/saucam/shiva',
              },
            ],
          },
        ],
        copyright: `Copyright © ${new Date().getFullYear()} saucam, Inc. Built with Docusaurus.`,
      },
      prism: {
        theme: lightCodeTheme,
        darkTheme: darkCodeTheme,
      },
    }),
};

module.exports = config;
